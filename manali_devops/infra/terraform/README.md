# Terraform (Milestone 2–4)

Planned modules / files:
```
main.tf  providers.tf  variables.tf  outputs.tf  backend.tf
network.tf     # VPC, 2 public + 2 private subnets, NAT (or VPC endpoints to save cost)
ecr.tf         # ekm-core-api, ekm-audit-service, ekm-ai-service, ekm-web
s3.tf          # ekm-documents-<env>-<account>, SSE-KMS, versioning, block public access
sqs.tf         # 3 queues + DLQs (names in contracts/ports-and-services.md)
rds.tf         # Postgres 16, pgvector, private
ecs.tf         # cluster, task defs, services, Service Connect
alb.tf         # /api/* -> core-api, /* -> web
iam.tf         # task roles per service (least privilege), GitHub OIDC deploy role
secrets.tf     # JWT_SECRET, OPENAI_API_KEY, INTERNAL_SERVICE_TOKEN, DB password
cloudwatch.tf  # log groups, alarms, dashboard, SNS
```
Environment values in `staging.tfvars` (git-ignored; commit `staging.tfvars.example`).
