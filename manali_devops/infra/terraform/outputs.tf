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

output "ecs_tasks_security_group_id" {
  description = "Security group ID to attach to ECS tasks."
  value       = aws_security_group.ecs_tasks.id
}

output "rds_security_group_id" {
  description = "RDS security group ID; ingress is restricted to ECS tasks."
  value       = aws_security_group.rds.id
}

output "rds_endpoint" {
  description = "Private RDS endpoint hostname."
  value       = aws_db_instance.postgres.address
}

output "rds_port" {
  description = "RDS PostgreSQL port."
  value       = aws_db_instance.postgres.port
}

output "rds_master_user_secret_arn" {
  description = "Secrets Manager ARN automatically created by RDS for the master credentials."
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

output "application_secret_arns" {
  description = "Secrets Manager ARNs for application secrets; values are intentionally not managed by Terraform."
  value       = { for name, secret in aws_secretsmanager_secret.application : name => secret.arn }
}

output "monthly_aws_budget_name" {
  description = "Monthly AWS cost budget name."
  value       = aws_budgets_budget.monthly_aws.name
}
