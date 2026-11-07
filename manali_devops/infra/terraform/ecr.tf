locals {
  ecr_repositories = toset([
    "core-api",
    "audit-service",
    "ai-service",
    "web",
  ])
}

resource "aws_ecr_repository" "service" {
  for_each = local.ecr_repositories

  name                 = "ekm-${var.environment}-${each.key}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Service = each.key
  }
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each = local.ecr_repositories

  repository = aws_ecr_repository.service[each.key].name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 10 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}
