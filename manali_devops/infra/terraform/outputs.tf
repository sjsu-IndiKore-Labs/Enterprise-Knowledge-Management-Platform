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
