data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

locals {
  name       = "${var.project}-${var.environment}"
  account_id = data.aws_caller_identity.current.account_id
  azs        = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
    Repository  = var.github_repository
  }

  # Resource names exactly as in contracts/ports-and-services.md
  documents_bucket = "${var.project}-documents-${var.environment}-${local.account_id}"
  queues = {
    document-ingest = { env = "SQS_DOCUMENT_INGEST_QUEUE", visibility_timeout = 300 }
    document-status = { env = "SQS_DOCUMENT_STATUS_QUEUE", visibility_timeout = 60 }
    audit-events    = { env = "SQS_AUDIT_EVENTS_QUEUE", visibility_timeout = 60 }
  }
  queue_names = { for k, _ in local.queues : k => "${local.name}-${k}" }

  # ECR repositories (ai-worker reuses the ai-service image)
  repositories = ["web", "core-api", "audit-service", "ai-service"]
  service_image = {
    web           = "web"
    core-api      = "core-api"
    audit-service = "audit-service"
    ai-service    = "ai-service"
    ai-worker     = "ai-service"
  }

  # Services reachable through the public ALB
  public_services = ["web", "core-api"]
  # Services registered in ECS Service Connect (DNS name = service key)
  service_connect_servers = ["ai-service"]

  app_url = var.app_domain != "" ? "https://${var.app_domain}" : (
    var.certificate_arn != "" ? "https://${aws_lb.this.dns_name}" : "http://${aws_lb.this.dns_name}"
  )

  ecs_subnet_ids = var.use_nat_gateway ? aws_subnet.private[*].id : aws_subnet.public[*].id
}
