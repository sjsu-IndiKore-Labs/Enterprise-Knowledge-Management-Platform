data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ---------------------------------------------------------------- execution role (pull images, logs, secrets)
resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [for s in aws_secretsmanager_secret.this : s.arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-app-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# ---------------------------------------------------------------- task roles (what the app code may do)
resource "aws_iam_role" "task" {
  for_each           = var.services
  name               = "${local.name}-${each.key}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "task" {
  for_each = var.services

  # ECS Exec (aws ecs execute-command) for debugging
  statement {
    sid = "EcsExec"
    actions = [
      "ssmmessages:CreateControlChannel", "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel", "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }

  # core-api: write/read/delete documents, send ingest + audit, consume status
  dynamic "statement" {
    for_each = each.key == "core-api" ? [1] : []
    content {
      sid       = "DocumentsReadWrite"
      actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
      resources = ["${aws_s3_bucket.documents.arn}/documents/*"]
    }
  }
  dynamic "statement" {
    for_each = each.key == "core-api" ? [1] : []
    content {
      sid       = "SendIngestAndAudit"
      actions   = ["sqs:SendMessage", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
      resources = [aws_sqs_queue.this["document-ingest"].arn, aws_sqs_queue.this["audit-events"].arn]
    }
  }
  dynamic "statement" {
    for_each = each.key == "core-api" ? [1] : []
    content {
      sid       = "ConsumeStatus"
      actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
      resources = [aws_sqs_queue.this["document-status"].arn]
    }
  }

  # ai-worker: read documents, consume ingest, send status
  dynamic "statement" {
    for_each = contains(["ai-worker", "ai-service"], each.key) ? [1] : []
    content {
      sid       = "DocumentsRead"
      actions   = ["s3:GetObject"]
      resources = ["${aws_s3_bucket.documents.arn}/documents/*"]
    }
  }
  dynamic "statement" {
    for_each = contains(["ai-worker", "ai-service"], each.key) ? [1] : []
    content {
      sid       = "ConsumeIngestSendStatus"
      actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:SendMessage", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
      resources = [aws_sqs_queue.this["document-ingest"].arn, aws_sqs_queue.this["document-status"].arn]
    }
  }

  # audit-service: consume audit events
  dynamic "statement" {
    for_each = each.key == "audit-service" ? [1] : []
    content {
      sid       = "ConsumeAudit"
      actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
      resources = [aws_sqs_queue.this["audit-events"].arn]
    }
  }

  # KMS for SSE-KMS objects
  dynamic "statement" {
    for_each = contains(["core-api", "ai-service", "ai-worker"], each.key) ? [1] : []
    content {
      sid       = "DocumentsKms"
      actions   = each.key == "core-api" ? ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey"] : ["kms:Decrypt"]
      resources = [aws_kms_key.documents.arn]
    }
  }
}

resource "aws_iam_role_policy" "task" {
  for_each = var.services
  name     = "app-permissions"
  role     = aws_iam_role.task[each.key].id
  policy   = data.aws_iam_policy_document.task[each.key].json
}
