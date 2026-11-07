resource "aws_iam_role" "core_api_task" {
  name               = "ekm-${var.environment}-core-api-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
}

resource "aws_iam_role" "ai_service_task" {
  name               = "ekm-${var.environment}-ai-service-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
}

resource "aws_iam_role" "audit_service_task" {
  name               = "ekm-${var.environment}-audit-service-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume_role.json
}

data "aws_iam_policy_document" "core_api_s3" {
  statement {
    sid       = "ListDocumentBucket"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [data.aws_s3_bucket.documents.arn]
  }

  statement {
    sid    = "ReadWriteDocuments"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject"
    ]
    resources = ["${data.aws_s3_bucket.documents.arn}/documents/*"]
  }

  statement {
    sid    = "UseDocumentEncryptionKey"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:Encrypt",
      "kms:GenerateDataKey"
    ]
    resources = [data.aws_kms_key.s3.arn]
  }
}

data "aws_iam_policy_document" "ai_service_s3" {
  statement {
    sid       = "ListDocumentBucket"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [data.aws_s3_bucket.documents.arn]
  }

  statement {
    sid       = "ReadDocuments"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${data.aws_s3_bucket.documents.arn}/documents/*"]
  }

  statement {
    sid       = "DecryptDocuments"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_key.s3.arn]
  }
}

data "aws_iam_policy_document" "core_api_sqs" {
  statement {
    sid     = "PublishIngestAndAuditEvents"
    effect  = "Allow"
    actions = ["sqs:SendMessage"]
    resources = [
      aws_sqs_queue.main["document_ingest"].arn,
      aws_sqs_queue.main["audit_events"].arn
    ]
  }

  statement {
    sid       = "ConsumeDocumentStatus"
    effect    = "Allow"
    actions   = ["sqs:DeleteMessage", "sqs:ReceiveMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.main["document_status"].arn]
  }
}

data "aws_iam_policy_document" "ai_service_sqs" {
  statement {
    sid       = "ConsumeDocumentIngest"
    effect    = "Allow"
    actions   = ["sqs:DeleteMessage", "sqs:ReceiveMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.main["document_ingest"].arn]
  }

  statement {
    sid       = "PublishDocumentStatus"
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.main["document_status"].arn]
  }
}

data "aws_iam_policy_document" "audit_service_sqs" {
  statement {
    sid       = "ConsumeAuditEvents"
    effect    = "Allow"
    actions   = ["sqs:DeleteMessage", "sqs:ReceiveMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.main["audit_events"].arn]
  }
}

resource "aws_iam_role_policy" "core_api_s3" {
  name   = "ekm-${var.environment}-core-api-s3"
  role   = aws_iam_role.core_api_task.id
  policy = data.aws_iam_policy_document.core_api_s3.json
}

resource "aws_iam_role_policy" "ai_service_s3" {
  name   = "ekm-${var.environment}-ai-service-s3"
  role   = aws_iam_role.ai_service_task.id
  policy = data.aws_iam_policy_document.ai_service_s3.json
}

resource "aws_iam_role_policy" "core_api_sqs" {
  name   = "ekm-${var.environment}-core-api-sqs"
  role   = aws_iam_role.core_api_task.id
  policy = data.aws_iam_policy_document.core_api_sqs.json
}

resource "aws_iam_role_policy" "ai_service_sqs" {
  name   = "ekm-${var.environment}-ai-service-sqs"
  role   = aws_iam_role.ai_service_task.id
  policy = data.aws_iam_policy_document.ai_service_sqs.json
}

resource "aws_iam_role_policy" "audit_service_sqs" {
  name   = "ekm-${var.environment}-audit-service-sqs"
  role   = aws_iam_role.audit_service_task.id
  policy = data.aws_iam_policy_document.audit_service_sqs.json
}
