locals {
  ecs_services = {
    core_api = {
      name          = "core-api"
      container     = "core-api"
      port          = 8080
      cpu           = 512
      memory        = 1024
      task_role_arn = aws_iam_role.core_api_task.arn
      public        = true
    }
    audit_service = {
      name          = "audit-service"
      container     = "audit-service"
      port          = 8081
      cpu           = 256
      memory        = 512
      task_role_arn = aws_iam_role.audit_service_task.arn
      public        = false
    }
    ai_service = {
      name          = "ai-service"
      container     = "ai-service"
      port          = 8000
      cpu           = 512
      memory        = 1024
      task_role_arn = aws_iam_role.ai_service_task.arn
      public        = false
    }
    web = {
      name          = "web"
      container     = "web"
      port          = 3000
      cpu           = 256
      memory        = 512
      task_role_arn = null
      public        = true
    }
  }
}

resource "aws_ecs_cluster" "main" {
  name = "ekm-${var.environment}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_log_group" "ecs" {
  for_each          = local.ecs_services
  name              = "/ecs/ekm-${var.environment}-${each.value.name}"
  retention_in_days = 14

  tags = {
    Environment = var.environment
    Service     = each.value.name
    ManagedBy   = "terraform"
  }
}

resource "aws_service_discovery_private_dns_namespace" "main" {
  name        = "ekm.local"
  description = "Private service discovery namespace for EKM staging ECS services."
  vpc         = aws_vpc.main.id
}

resource "aws_service_discovery_service" "internal" {
  for_each = {
    ai_service    = local.ecs_services.ai_service
    audit_service = local.ecs_services.audit_service
  }

  name = each.value.name

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_ecs_task_definition" "service" {
  for_each = local.ecs_services

  family                   = "ekm-${var.environment}-${each.value.name}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = tostring(each.value.cpu)
  memory                   = tostring(each.value.memory)
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = each.value.task_role_arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name         = each.value.container
      image        = "${aws_ecr_repository.service[each.value.name].repository_url}:${var.ecs_image_tag}"
      essential    = true
      stopTimeout  = 60
      portMappings = [{ name = each.value.container, containerPort = each.value.port, hostPort = each.value.port, protocol = "tcp" }]
      environment = [
        { name = "APP_ENV", value = var.environment },
        { name = "LOG_LEVEL", value = "INFO" },
        { name = "LOG_FORMAT", value = "json" },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "AWS_ENDPOINT_URL", value = "" },
        { name = "S3_DOCUMENTS_BUCKET", value = var.s3_documents_bucket },
        { name = "SQS_DOCUMENT_INGEST_QUEUE", value = aws_sqs_queue.main["document_ingest"].name },
        { name = "SQS_DOCUMENT_STATUS_QUEUE", value = aws_sqs_queue.main["document_status"].name },
        { name = "SQS_AUDIT_EVENTS_QUEUE", value = aws_sqs_queue.main["audit_events"].name },
        { name = "POSTGRES_HOST", value = aws_db_instance.postgres.address },
        { name = "POSTGRES_PORT", value = tostring(aws_db_instance.postgres.port) },
        { name = "POSTGRES_DB", value = var.rds_database_name },
        { name = "POSTGRES_USER", value = var.rds_master_username },
        { name = "CORS_ALLOWED_ORIGINS", value = "http://${aws_lb.app.dns_name}" },
        { name = "CORE_API_PORT", value = "8080" },
        { name = "AUDIT_SERVICE_PORT", value = "8081" },
        { name = "AI_SERVICE_PORT", value = "8000" },
        { name = "AI_SERVICE_URL", value = "http://ai-service.ekm.local:8000" },
        { name = "NEXT_PUBLIC_API_BASE_URL", value = "http://${aws_lb.app.dns_name}" },
        { name = "AI_PROVIDER", value = "openai" },
        { name = "OPENAI_CHAT_MODEL", value = "gpt-4o-mini" },
        { name = "OPENAI_EMBEDDING_MODEL", value = "text-embedding-3-small" },
        { name = "EMBEDDING_DIMENSIONS", value = "1536" }
      ]
      secrets = each.value.name == "core-api" ? [
        { name = "POSTGRES_PASSWORD", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::" },
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.application["jwt-secret"].arn },
        { name = "INTERNAL_SERVICE_TOKEN", valueFrom = aws_secretsmanager_secret.application["internal-service-token"].arn }
        ] : each.value.name == "ai-service" ? [
        { name = "POSTGRES_PASSWORD", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::" },
        { name = "OPENAI_API_KEY", valueFrom = aws_secretsmanager_secret.application["openai-api-key"].arn },
        { name = "INTERNAL_SERVICE_TOKEN", valueFrom = aws_secretsmanager_secret.application["internal-service-token"].arn }
        ] : each.value.name == "audit-service" ? [
        { name = "POSTGRES_PASSWORD", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::" },
        { name = "INTERNAL_SERVICE_TOKEN", valueFrom = aws_secretsmanager_secret.application["internal-service-token"].arn }
      ] : []
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.ecs[each.key].name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = "ecs"
        }
      }
    }
  ])

  tags = {
    Environment = var.environment
    Service     = each.value.name
    ManagedBy   = "terraform"
  }
}

resource "aws_ecs_service" "service" {
  for_each = local.ecs_services

  name             = "ekm-${var.environment}-${each.value.name}"
  cluster          = aws_ecs_cluster.main.id
  task_definition  = aws_ecs_task_definition.service[each.key].arn
  desired_count    = var.ecs_desired_count
  launch_type      = "FARGATE"
  platform_version = "LATEST"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  dynamic "load_balancer" {
    for_each = each.value.public ? [each.key] : []
    content {
      target_group_arn = each.key == "core_api" ? aws_lb_target_group.core_api.arn : aws_lb_target_group.web.arn
      container_name   = each.value.container
      container_port   = each.value.port
    }
  }

  dynamic "service_registries" {
    for_each = contains(["ai_service", "audit_service"], each.key) ? [each.key] : []
    content {
      registry_arn = aws_service_discovery_service.internal[each.key].arn
    }
  }

  depends_on = [aws_iam_role_policy_attachment.ecs_execution_managed, aws_iam_role_policy.ecs_execution_secrets]

  tags = {
    Environment = var.environment
    Service     = each.value.name
    ManagedBy   = "terraform"
  }
}
