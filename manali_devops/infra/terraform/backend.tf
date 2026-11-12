terraform {
  backend "s3" {
    bucket         = "ekm-terraform-state-474632925730"
    key            = "staging/terraform.tfstate"
    region         = "us-west-2"
    dynamodb_table = "ekm-terraform-locks"
    encrypt        = true
  }
}
