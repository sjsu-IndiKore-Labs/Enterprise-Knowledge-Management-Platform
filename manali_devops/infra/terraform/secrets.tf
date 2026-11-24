# Secret values never appear in task definitions; ECS injects them at container start.
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "internal_token" {
  length  = 48
  special = false
}

locals {
  generated_secrets = {
    POSTGRES_PASSWORD      = random_password.db.result
    JWT_SECRET             = random_password.jwt.result
    INTERNAL_SERVICE_TOKEN = random_password.internal_token.result
  }
  secret_names = ["POSTGRES_PASSWORD", "JWT_SECRET", "INTERNAL_SERVICE_TOKEN", "OPENAI_API_KEY"]

  # Least privilege: which secrets each container receives
  service_secrets = {
    web           = []
    core-api      = ["POSTGRES_PASSWORD", "JWT_SECRET", "INTERNAL_SERVICE_TOKEN"]
    audit-service = ["POSTGRES_PASSWORD"]
    ai-service    = ["POSTGRES_PASSWORD", "INTERNAL_SERVICE_TOKEN", "OPENAI_API_KEY"]
    ai-worker     = ["POSTGRES_PASSWORD", "OPENAI_API_KEY"]
  }
}

resource "aws_secretsmanager_secret" "this" {
  for_each                = toset(local.secret_names)
  name                    = "${var.project}/${var.environment}/${lower(replace(each.key, "_", "-"))}"
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "generated" {
  for_each      = local.generated_secrets
  secret_id     = aws_secretsmanager_secret.this[each.key].id
  secret_string = each.value
}

# The OpenAI key is set by hand after the first apply (see manali_devops/docs/aws-deployment.md):
#   aws secretsmanager put-secret-value --secret-id ekm/staging/openai-api-key --secret-string sk-...
resource "aws_secretsmanager_secret_version" "openai_placeholder" {
  secret_id     = aws_secretsmanager_secret.this["OPENAI_API_KEY"].id
  secret_string = "REPLACE_ME"
  lifecycle {
    ignore_changes = [secret_string]
  }
}
