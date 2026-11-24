terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # Partial config: terraform init -backend-config=environments/<env>.backend.hcl
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = local.tags
  }
}
