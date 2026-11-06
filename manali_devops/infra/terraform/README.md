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
Shared environment values live in the repository-root `staging.tfvars.example`.
Copy it to `staging.tfvars` (git-ignored) before applying Terraform. Never add secrets
to either file; runtime secrets belong in AWS Secrets Manager.

The current Terraform foundation defines ECS task roles for the existing staging bucket:

- `core-api` can list and read/write `documents/*` and use the S3 KMS key.
- `ai-service` can list/read `documents/*` and decrypt the S3 KMS key.
- `web` and `audit-service` receive no S3 permissions.

Run from this directory:

```bash
cp ../../staging.tfvars.example staging.tfvars
terraform init
terraform plan -var-file=staging.tfvars
```

The existing bucket must be referenced or imported into state before applying. The task
roles can later be attached to ECS task definitions in the Milestone 4 configuration.
