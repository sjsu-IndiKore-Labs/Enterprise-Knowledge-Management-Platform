# Offline unit tests for the staging stack: `terraform test` (no AWS credentials needed).
# Mock providers fake every AWS response; assertions check the configuration we generate.

mock_provider "aws" {
  override_data {
    target = data.aws_availability_zones.available
    values = { names = ["us-west-2a", "us-west-2b", "us-west-2c"] }
  }
  override_data {
    target = data.aws_caller_identity.current
    values = { account_id = "123456789012" }
  }
  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:us-west-2:123456789012:loadbalancer/app/ekm-staging/abc", arn_suffix = "app/ekm-staging/abc", dns_name = "ekm-staging-123.us-west-2.elb.amazonaws.com" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:us-west-2:123456789012:targetgroup/ekm/abc", arn_suffix = "targetgroup/ekm/abc" }
  }
  mock_resource "aws_lb_listener" {
    defaults = { arn = "arn:aws:elasticloadbalancing:us-west-2:123456789012:listener/app/ekm-staging/abc/def" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/ekm-mock" }
  }
  mock_resource "aws_iam_openid_connect_provider" {
    defaults = { arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com" }
  }
  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:secretsmanager:us-west-2:123456789012:secret:ekm/mock-AbCdEf" }
  }
  mock_resource "aws_sqs_queue" {
    defaults = { arn = "arn:aws:sqs:us-west-2:123456789012:ekm-mock", url = "https://sqs.us-west-2.amazonaws.com/123456789012/ekm-mock" }
  }
  mock_resource "aws_kms_key" {
    defaults = { arn = "arn:aws:kms:us-west-2:123456789012:key/11111111-2222-3333-4444-555555555555", key_id = "11111111-2222-3333-4444-555555555555" }
  }
  mock_resource "aws_s3_bucket" {
    defaults = { arn = "arn:aws:s3:::ekm-mock" }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:us-west-2:123456789012:ekm-mock" }
  }
  mock_resource "aws_service_discovery_http_namespace" {
    defaults = { arn = "arn:aws:servicediscovery:us-west-2:123456789012:namespace/ns-mock" }
  }
  mock_resource "aws_ecs_cluster" {
    defaults = { arn = "arn:aws:ecs:us-west-2:123456789012:cluster/ekm-staging", id = "arn:aws:ecs:us-west-2:123456789012:cluster/ekm-staging" }
  }
  mock_resource "aws_ecs_task_definition" {
    defaults = { arn = "arn:aws:ecs:us-west-2:123456789012:task-definition/ekm-mock:1" }
  }
  mock_resource "aws_ecr_repository" {
    defaults = { arn = "arn:aws:ecr:us-west-2:123456789012:repository/ekm-mock", repository_url = "123456789012.dkr.ecr.us-west-2.amazonaws.com/ekm-mock" }
  }
  mock_resource "aws_db_instance" {
    defaults = { arn = "arn:aws:rds:us-west-2:123456789012:db:ekm-staging", address = "ekm-staging.abc.us-west-2.rds.amazonaws.com", port = 5432 }
  }
  mock_resource "aws_eip" {
    defaults = { id = "eipalloc-0123456789abcdef0" }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
}

mock_provider "random" {}

variables {
  environment = "staging"
}

run "names_follow_contracts" {
  command = apply

  assert {
    condition     = aws_s3_bucket.documents.bucket == "ekm-documents-staging-123456789012"
    error_message = "Bucket name must be ekm-documents-<env>-<accountId> (contracts/ports-and-services.md)"
  }
  assert {
    condition = (aws_sqs_queue.this["document-ingest"].name == "ekm-staging-document-ingest" &&
      aws_sqs_queue.this["document-status"].name == "ekm-staging-document-status" &&
    aws_sqs_queue.this["audit-events"].name == "ekm-staging-audit-events")
    error_message = "Queue names must be ekm-<env>-<queue> (contracts/ports-and-services.md)"
  }
  assert {
    condition     = alltrue([for k, q in aws_sqs_queue.dlq : q.name == "${aws_sqs_queue.this[k].name}-dlq"])
    error_message = "Every queue needs a <name>-dlq"
  }
  assert {
    condition     = toset(keys(aws_ecr_repository.this)) == toset(["web", "core-api", "audit-service", "ai-service"])
    error_message = "One ECR repository per image"
  }
  assert {
    condition     = toset(keys(aws_ecs_service.this)) == toset(["web", "core-api", "audit-service", "ai-service", "ai-worker"])
    error_message = "Five ECS services expected"
  }
}

run "queues_match_event_contract" {
  command = apply

  assert {
    condition     = alltrue([for k, q in aws_sqs_queue.this : jsondecode(q.redrive_policy).maxReceiveCount == 3])
    error_message = "maxReceiveCount must be 3 (contracts/events/README.md)"
  }
  assert {
    condition     = aws_sqs_queue.this["document-ingest"].visibility_timeout_seconds == 300
    error_message = "Ingest visibility timeout must be 300 s"
  }
  assert {
    condition     = alltrue([for q in aws_sqs_queue.this : q.receive_wait_time_seconds == 20 && q.sqs_managed_sse_enabled])
    error_message = "Queues must use long polling and SSE"
  }
}

run "data_is_encrypted_and_private" {
  command = apply

  assert {
    condition = (aws_s3_bucket_public_access_block.documents.block_public_acls &&
      aws_s3_bucket_public_access_block.documents.block_public_policy &&
      aws_s3_bucket_public_access_block.documents.ignore_public_acls &&
    aws_s3_bucket_public_access_block.documents.restrict_public_buckets)
    error_message = "Documents bucket must block all public access"
  }
  assert {
    condition     = one([for r in aws_s3_bucket_server_side_encryption_configuration.documents.rule : r.apply_server_side_encryption_by_default[0].sse_algorithm]) == "aws:kms"
    error_message = "Documents bucket must use SSE-KMS"
  }
  assert {
    condition     = aws_kms_key.documents.enable_key_rotation
    error_message = "KMS key rotation must be on"
  }
  assert {
    condition     = !aws_db_instance.this.publicly_accessible && aws_db_instance.this.storage_encrypted
    error_message = "RDS must be private and encrypted"
  }
  assert {
    condition     = one([for p in aws_db_parameter_group.this.parameter : p.value if p.name == "rds.force_ssl"]) == "1"
    error_message = "RDS must force TLS"
  }
}

run "task_definitions" {
  command = apply

  assert {
    condition     = alltrue([for t in aws_ecs_task_definition.this : t.network_mode == "awsvpc" && contains(t.requires_compatibilities, "FARGATE")])
    error_message = "All tasks must be Fargate/awsvpc"
  }
  assert {
    condition = alltrue(flatten([
      for k, t in aws_ecs_task_definition.this : [
        for e in jsondecode(t.container_definitions)[0].environment :
        e.name == "PORT" || can(regex("(?m)^${e.name}=", file("${path.module}/../../../.env.example")))
      ]
    ]))
    error_message = "Every env var injected into a task definition must be declared in .env.example"
  }
  assert {
    condition     = contains([for e in jsondecode(aws_ecs_task_definition.this["core-api"].container_definitions)[0].environment : "${e.name}=${e.value}"], "POSTGRES_SSLMODE=require")
    error_message = "Backends must connect to RDS with sslmode=require"
  }
  assert {
    condition     = contains([for e in jsondecode(aws_ecs_task_definition.this["core-api"].container_definitions)[0].environment : "${e.name}=${e.value}"], "AI_SERVICE_URL=http://ai-service:8000")
    error_message = "core-api must reach ai-service through Service Connect at http://ai-service:8000"
  }
  assert {
    condition     = length(jsondecode(aws_ecs_task_definition.this["web"].container_definitions)[0].secrets) == 0
    error_message = "web must not receive any secret"
  }
  assert {
    condition     = !contains([for s in jsondecode(aws_ecs_task_definition.this["audit-service"].container_definitions)[0].secrets : s.name], "OPENAI_API_KEY")
    error_message = "audit-service must not receive the OpenAI key"
  }
  assert {
    condition     = jsondecode(aws_ecs_task_definition.this["ai-worker"].container_definitions)[0].command == ["python", "-m", "app.ingestion.worker"]
    error_message = "ai-worker runs the ingestion worker module"
  }
  assert {
    condition     = length(jsondecode(aws_ecs_task_definition.this["ai-worker"].container_definitions)[0].portMappings) == 0
    error_message = "ai-worker exposes no port"
  }
  assert {
    condition     = alltrue([for t in aws_ecs_task_definition.this : !strcontains(t.container_definitions, "REPLACE_ME")])
    error_message = "No secret value may appear in a task definition"
  }
}

run "routing_and_network" {
  command = apply

  assert {
    condition     = one(aws_lb_listener_rule.core_api.condition).path_pattern[0].values == toset(["/api/v1/*"])
    error_message = "ALB must route /api/v1/* to core-api"
  }
  assert {
    condition     = length(aws_ecs_service.this["web"].load_balancer) == 1 && length(aws_ecs_service.this["ai-service"].load_balancer) == 0
    error_message = "Only web and core-api are behind the public ALB"
  }
  assert {
    condition     = length(aws_nat_gateway.this) == 0 && one(aws_ecs_service.this["core-api"].network_configuration).assign_public_ip
    error_message = "Default (cheap) mode: no NAT, tasks get public IPs"
  }
  assert {
    condition     = aws_lb_listener.http.default_action[0].type == "forward"
    error_message = "Without a certificate, HTTP forwards to web"
  }
  assert {
    condition     = toset(keys(aws_appautoscaling_target.this)) == toset(["web", "core-api", "ai-service", "ai-worker"])
    error_message = "Every service with max_count > min_count autoscales (audit-service is fixed at 1)"
  }
}

run "https_mode" {
  command = apply
  variables {
    certificate_arn = "arn:aws:acm:us-west-2:123456789012:certificate/test"
  }
  assert {
    condition     = length(aws_lb_listener.https) == 1 && aws_lb_listener.http.default_action[0].type == "redirect"
    error_message = "With a certificate, 80 redirects to 443"
  }
  assert {
    condition     = startswith(output.app_url, "https://")
    error_message = "app_url must be https"
  }
}

run "private_subnets_mode" {
  command = apply
  variables {
    use_nat_gateway = true
  }
  assert {
    condition     = length(aws_nat_gateway.this) == 1 && !one(aws_ecs_service.this["core-api"].network_configuration).assign_public_ip
    error_message = "NAT mode: tasks in private subnets without public IPs"
  }
}

run "rejects_single_az" {
  command = plan
  variables {
    az_count = 1
  }
  expect_failures = [var.az_count]
}
