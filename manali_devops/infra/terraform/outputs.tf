output "core_api_task_role_arn" {
  description = "ECS task role ARN for core-api."
  value       = aws_iam_role.core_api_task.arn
}

output "ai_service_task_role_arn" {
  description = "ECS task role ARN for ai-service."
  value       = aws_iam_role.ai_service_task.arn
}

output "audit_service_task_role_arn" {
  description = "ECS task role ARN for audit-service."
  value       = aws_iam_role.audit_service_task.arn
}

output "vpc_id" {
  description = "Staging VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Staging public subnet IDs."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Staging private subnet IDs."
  value       = aws_subnet.private[*].id
}

output "ecr_repository_urls" {
  description = "Staging ECR repository URLs by service."
  value       = { for service, repository in aws_ecr_repository.service : service => repository.repository_url }
}
