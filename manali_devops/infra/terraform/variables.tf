variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "staging"
}

variable "aws_region" {
  description = "AWS region for the deployment."
  type        = string
  default     = "us-west-2"
}

variable "s3_documents_bucket" {
  description = "Existing private S3 bucket used for document storage."
  type        = string
}

variable "s3_kms_key_arn" {
  description = "KMS key or alias ARN used for S3 SSE-KMS encryption."
  type        = string
}
