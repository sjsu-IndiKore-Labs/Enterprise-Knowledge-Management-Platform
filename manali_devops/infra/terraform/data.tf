data "aws_s3_bucket" "documents" {
  bucket = var.s3_documents_bucket
}

data "aws_kms_key" "s3" {
  key_id = var.s3_kms_key_arn
}

data "aws_kms_key" "rds" {
  key_id = var.rds_kms_key_arn
}

data "aws_iam_policy_document" "ecs_task_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}
