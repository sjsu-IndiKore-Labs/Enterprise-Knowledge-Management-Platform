output "app_url" {
  description = "Public URL of the portal (web on /, core-api on /api/v1/*)."
  value       = local.app_url
}

output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "ecs_cluster" {
  value = aws_ecs_cluster.this.name
}

output "ecs_services" {
  value = { for k, s in aws_ecs_service.this : k => s.name }
}

output "task_definition_families" {
  value = { for k, t in aws_ecs_task_definition.this : k => t.family }
}

output "documents_bucket" {
  value = aws_s3_bucket.documents.bucket
}

output "queue_urls" {
  value = merge(
    { for k, q in aws_sqs_queue.this : k => q.url },
    { for k, q in aws_sqs_queue.dlq : "${k}-dlq" => q.url }
  )
}

output "rds_endpoint" {
  value = aws_db_instance.this.address
}

output "secret_ids" {
  description = "Set the OpenAI key with: aws secretsmanager put-secret-value --secret-id <OPENAI_API_KEY id> --secret-string sk-..."
  value       = { for k, s in aws_secretsmanager_secret.this : k => s.name }
}

output "github_deploy_role_arn" {
  description = "Put this in the GitHub repository variable AWS_DEPLOY_ROLE_ARN."
  value       = aws_iam_role.github_deploy.arn
}

output "cloudwatch_dashboard_url" {
  value = "https://${var.aws_region}.console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.this.dashboard_name}"
}
