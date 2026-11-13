locals {
  application_secret_names = toset([
    "jwt-secret",
    "openai-api-key",
    "internal-service-token",
  ])
}

resource "aws_secretsmanager_secret" "application" {
  for_each = local.application_secret_names

  name                    = "ekm/${var.environment}/${each.key}"
  description             = "EKM ${var.environment} application secret: ${each.key}"
  recovery_window_in_days = 7

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

data "aws_iam_policy_document" "core_api_secrets" {
  statement {
    sid     = "ReadCoreApiSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:DescribeSecret", "secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.application["jwt-secret"].arn,
      aws_secretsmanager_secret.application["internal-service-token"].arn,
      aws_db_instance.postgres.master_user_secret[0].secret_arn,
    ]
  }
}

data "aws_iam_policy_document" "ai_service_secrets" {
  statement {
    sid     = "ReadAiServiceSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:DescribeSecret", "secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.application["openai-api-key"].arn,
      aws_secretsmanager_secret.application["internal-service-token"].arn,
      aws_db_instance.postgres.master_user_secret[0].secret_arn,
    ]
  }
}

data "aws_iam_policy_document" "audit_service_secrets" {
  statement {
    sid     = "ReadAuditServiceSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:DescribeSecret", "secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.application["internal-service-token"].arn,
      aws_db_instance.postgres.master_user_secret[0].secret_arn,
    ]
  }
}

resource "aws_iam_role_policy" "core_api_secrets" {
  name   = "ekm-${var.environment}-core-api-secrets"
  role   = aws_iam_role.core_api_task.id
  policy = data.aws_iam_policy_document.core_api_secrets.json
}

resource "aws_iam_role_policy" "ai_service_secrets" {
  name   = "ekm-${var.environment}-ai-service-secrets"
  role   = aws_iam_role.ai_service_task.id
  policy = data.aws_iam_policy_document.ai_service_secrets.json
}

resource "aws_iam_role_policy" "audit_service_secrets" {
  name   = "ekm-${var.environment}-audit-service-secrets"
  role   = aws_iam_role.audit_service_task.id
  policy = data.aws_iam_policy_document.audit_service_secrets.json
}
