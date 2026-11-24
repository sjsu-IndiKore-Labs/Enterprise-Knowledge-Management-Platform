variable "project" {
  description = "Short project prefix used in every resource name."
  type        = string
  default     = "ekm"
}

variable "environment" {
  description = "Environment name (staging, prod)."
  type        = string
  default     = "staging"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,15}$", var.environment))
    error_message = "environment must be lowercase letters, digits or dashes."
  }
}

variable "aws_region" {
  type    = string
  default = "us-west-2"
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones (ALB and RDS subnet groups need >= 2)."
  type        = number
  default     = 2
  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count must be 2 or 3."
  }
}

variable "use_nat_gateway" {
  description = <<-EOT
    false (default, cheaper): ECS tasks run in public subnets with a public IP; security groups only allow
    traffic from the ALB / other tasks. true: tasks run in private subnets behind one NAT gateway (~$32/month).
  EOT
  type        = bool
  default     = false
}

variable "cpu_architecture" {
  description = "Fargate CPU architecture. Must match the platform the CI builds images for."
  type        = string
  default     = "X86_64"
  validation {
    condition     = contains(["X86_64", "ARM64"], var.cpu_architecture)
    error_message = "cpu_architecture must be X86_64 or ARM64."
  }
}

variable "image_tag" {
  description = "Image tag used when Terraform (re)creates task definitions. CD deploys newer tags without Terraform."
  type        = string
  default     = "bootstrap"
}

variable "services" {
  description = "ECS service sizing. port = null for services without HTTP (ai-worker)."
  type = map(object({
    cpu           = number
    memory        = number
    desired_count = number
    min_count     = number
    max_count     = number
    port          = optional(number)
    health_path   = optional(string)
  }))
  default = {
    web           = { cpu = 256, memory = 512, desired_count = 1, min_count = 1, max_count = 2, port = 3000, health_path = "/api/health" }
    core-api      = { cpu = 512, memory = 1024, desired_count = 1, min_count = 1, max_count = 3, port = 8080, health_path = "/actuator/health" }
    audit-service = { cpu = 256, memory = 1024, desired_count = 1, min_count = 1, max_count = 1, port = 8081, health_path = "/actuator/health" }
    ai-service    = { cpu = 512, memory = 1024, desired_count = 1, min_count = 1, max_count = 3, port = 8000, health_path = "/health" }
    ai-worker     = { cpu = 512, memory = 1024, desired_count = 1, min_count = 1, max_count = 2 }
  }
  validation {
    condition     = alltrue([for k in ["web", "core-api", "audit-service", "ai-service", "ai-worker"] : contains(keys(var.services), k)])
    error_message = "services must define web, core-api, audit-service, ai-service and ai-worker."
  }
}

variable "certificate_arn" {
  description = "Optional ACM certificate ARN. When set, the ALB serves HTTPS on 443 and redirects 80 -> 443."
  type        = string
  default     = ""
}

variable "app_domain" {
  description = "Optional custom domain pointing at the ALB (used for CORS). Empty = ALB DNS name."
  type        = string
  default     = ""
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "PostgreSQL major version (RDS picks the latest minor). pgvector is available on RDS PostgreSQL 15+."
  type        = string
  default     = "16"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_deletion_protection" {
  type    = bool
  default = false
}

variable "db_skip_final_snapshot" {
  description = "true for staging (fast teardown). Set false for anything holding real data."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "alarm_email" {
  description = "Email subscribed to the alarm SNS topic (confirm the subscription email). Empty = no subscription."
  type        = string
  default     = ""
}

variable "monthly_budget_usd" {
  description = "AWS Budgets monthly cost limit; alerts at 80% actual and 100% forecast. 0 disables."
  type        = number
  default     = 150
}

variable "enable_container_insights" {
  description = "ECS Container Insights (extra CloudWatch cost)."
  type        = bool
  default     = false
}

variable "secret_recovery_window_days" {
  description = "0 = delete secrets immediately on destroy (staging)."
  type        = number
  default     = 0
}

variable "github_repository" {
  description = "owner/name allowed to assume the deploy role via OIDC."
  type        = string
  default     = "sjsu-IndiKore-Labs/Enterprise-Knowledge-Management-Platform"
}

variable "create_github_oidc_provider" {
  description = "Create the GitHub OIDC provider (only one per AWS account; set false if it already exists)."
  type        = bool
  default     = true
}

variable "app_config" {
  description = "Non-secret application settings injected into task definitions (names from .env.example)."
  type        = map(string)
  default = {
    LOG_LEVEL                   = "INFO"
    LOG_FORMAT                  = "json"
    JWT_ISSUER                  = "ekm-core-api"
    JWT_EXPIRATION_MINUTES      = "60"
    AI_PROVIDER                 = "openai"
    OPENAI_CHAT_MODEL           = "gpt-4o-mini"
    OPENAI_EMBEDDING_MODEL      = "text-embedding-3-small"
    EMBEDDING_DIMENSIONS        = "1536"
    CHUNK_SIZE_TOKENS           = "500"
    CHUNK_OVERLAP_TOKENS        = "80"
    RAG_TOP_K                   = "6"
    RAG_MIN_SIMILARITY          = "0.35"
    RAG_HYBRID_SEMANTIC_WEIGHT  = "0.7"
    EMBEDDING_CACHE_TTL_SECONDS = "3600"
  }
}
