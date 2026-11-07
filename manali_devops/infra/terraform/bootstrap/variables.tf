variable "aws_region" {
  description = "AWS region for Terraform state resources."
  type        = string
  default     = "us-west-2"
}

variable "state_bucket_name" {
  description = "Globally unique S3 bucket name for Terraform state."
  type        = string
  default     = "ekm-terraform-state-474632925730"
}

variable "lock_table_name" {
  description = "DynamoDB table name used for Terraform state locking."
  type        = string
  default     = "ekm-terraform-locks"
}
