resource "aws_ecs_cluster" "this" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = var.enable_container_insights ? "enabled" : "disabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

# Service Connect: core-api reaches ai-service at http://ai-service:8000 (same URL as docker-compose)
resource "aws_service_discovery_http_namespace" "this" {
  name        = "${local.name}.local"
  description = "EKM service-to-service namespace"
}

resource "aws_cloudwatch_log_group" "service" {
  for_each          = var.services
  name              = "/ecs/${local.name}/${each.key}"
  retention_in_days = var.log_retention_days
}

locals {
  # Non-secret env shared by backend containers. Names come from .env.example only.
  backend_env = merge(var.app_config, {
    APP_ENV    = var.environment
    AWS_REGION = var.aws_region
    # The AI image defaults to LocalStack for local development. Explicitly
    # clear local-only AWS settings in ECS so the SDK uses the task role.
    AWS_ENDPOINT_URL          = ""
    AWS_ACCESS_KEY_ID         = ""
    AWS_SECRET_ACCESS_KEY     = ""
    S3_DOCUMENTS_BUCKET       = aws_s3_bucket.documents.bucket
    SQS_DOCUMENT_INGEST_QUEUE = try(aws_sqs_queue.this["document-ingest"].name, "")
    SQS_DOCUMENT_STATUS_QUEUE = try(aws_sqs_queue.this["document-status"].name, "")
    SQS_AUDIT_EVENTS_QUEUE    = try(aws_sqs_queue.this["audit-events"].name, "")
    POSTGRES_HOST             = aws_db_instance.this.address
    POSTGRES_PORT             = tostring(aws_db_instance.this.port)
    POSTGRES_DB               = aws_db_instance.this.db_name
    POSTGRES_USER             = aws_db_instance.this.username
    POSTGRES_SSLMODE          = "require"
    CORE_API_PORT             = "8080"
    AUDIT_SERVICE_PORT        = "8081"
    AI_SERVICE_PORT           = "8000"
    AI_SERVICE_URL            = "http://ai-service:8000"
    CORS_ALLOWED_ORIGINS      = local.app_url
  })

  service_env = {
    web           = { APP_ENV = var.environment, PORT = "3000" }
    core-api      = merge(local.backend_env, { PORT = "8080" })
    audit-service = merge(local.backend_env, { PORT = "8081" })
    ai-service    = merge(local.backend_env, { PORT = "8000" })
    ai-worker     = local.backend_env
  }

  service_command = {
    ai-worker = ["python", "-m", "app.ingestion.worker"]
  }

  health_command = {
    for k, s in var.services : k => (
      s.port == null ? null :
      startswith(k, "ai-")
      ? ["CMD-SHELL", "python -c \"import urllib.request; urllib.request.urlopen('http://127.0.0.1:${s.port}${s.health_path}', timeout=3)\" || exit 1"]
      : ["CMD-SHELL", "wget -qO- http://127.0.0.1:${s.port}${s.health_path} >/dev/null || exit 1"]
    )
  }

  container_definitions = {
    for k, s in var.services : k => [merge(
      {
        name                   = k
        image                  = "${aws_ecr_repository.this[local.service_image[k]].repository_url}:${var.image_tag}"
        essential              = true
        readonlyRootFilesystem = false
        environment            = [for name, value in local.service_env[k] : { name = name, value = value }]
        # Import/bootstrap operations can evaluate a partial resource graph
        # before every secret instance exists in state. Keep those operations
        # evaluable; a full plan includes every configured secret below.
        secrets = [for name in local.service_secrets[k] : {
          name      = name
          valueFrom = aws_secretsmanager_secret.this[name].arn
        } if contains(keys(aws_secretsmanager_secret.this), name)]
        portMappings = s.port == null ? [] : [{
          name          = "http"
          containerPort = s.port
          protocol      = "tcp"
          appProtocol   = "http"
        }]
        logConfiguration = {
          logDriver = "awslogs"
          options = {
            awslogs-group         = aws_cloudwatch_log_group.service[k].name
            awslogs-region        = var.aws_region
            awslogs-stream-prefix = k
          }
        }
      },
      lookup(local.service_command, k, null) == null ? {} : { command = local.service_command[k] },
      local.health_command[k] == null ? {} : {
        healthCheck = { command = local.health_command[k], interval = 15, timeout = 5, retries = 5, startPeriod = 90 }
      }
    )]
  }
}

resource "aws_ecs_task_definition" "this" {
  for_each                 = var.services
  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task[each.key].arn
  container_definitions    = jsonencode(local.container_definitions[each.key])

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }
}

resource "aws_ecs_service" "this" {
  for_each               = var.services
  name                   = "${local.name}-${each.key}"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.this[each.key].arn
  desired_count          = each.value.desired_count
  launch_type            = "FARGATE"
  enable_execute_command = true
  propagate_tags         = "SERVICE"

  health_check_grace_period_seconds = contains(local.public_services, each.key) ? 120 : null

  network_configuration {
    subnets          = local.ecs_subnet_ids
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = !var.use_nat_gateway
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  dynamic "load_balancer" {
    for_each = contains(local.public_services, each.key) ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.this[each.key].arn
      container_name   = each.key
      container_port   = each.value.port
    }
  }

  dynamic "service_connect_configuration" {
    for_each = contains(["core-api", "ai-service"], each.key) ? [1] : []
    content {
      enabled   = true
      namespace = aws_service_discovery_http_namespace.this.arn
      dynamic "service" {
        for_each = contains(local.service_connect_servers, each.key) ? [1] : []
        content {
          port_name      = "http"
          discovery_name = each.key
          client_alias {
            port     = each.value.port
            dns_name = each.key
          }
        }
      }
    }
  }

  # CD (GitHub Actions / Jenkins) registers new task definition revisions with new image tags,
  # and autoscaling changes desired_count — Terraform must not revert either.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener.http]
}

# ---------------------------------------------------------------- autoscaling (CPU target tracking)
resource "aws_appautoscaling_target" "this" {
  for_each           = { for k, s in var.services : k => s if s.max_count > s.min_count }
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.this[each.key].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = each.value.min_count
  max_capacity       = each.value.max_count
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each           = { for k, s in var.services : k => s if s.max_count > s.min_count }
  name               = "${local.name}-${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.this[each.key].service_namespace
  resource_id        = aws_appautoscaling_target.this[each.key].resource_id
  scalable_dimension = aws_appautoscaling_target.this[each.key].scalable_dimension
  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
