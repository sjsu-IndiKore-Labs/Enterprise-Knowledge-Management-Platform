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

variable "s3_bucket_key_enabled" {
  description = "Whether S3 Bucket Keys are enabled for SSE-KMS objects."
  type        = bool
  default     = true
}

variable "s3_bucket_versioning_enabled" {
  description = "Whether versioning is enabled on the document bucket."
  type        = bool
  default     = true
}

variable "s3_block_public_access" {
  description = "Whether public access is blocked on the document bucket."
  type        = bool
  default     = true
}

variable "s3_allowed_origins" {
  description = "Browser origins allowed to use the document bucket CORS configuration."
  type        = list(string)
  default     = ["http://localhost:3000"]
}
