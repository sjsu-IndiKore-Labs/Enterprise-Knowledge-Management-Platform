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

The repository-root `staging.env.example` contains the non-secret runtime names for ECS:
the staging S3 bucket, SQS queues, AWS region, and service settings. ECS task definitions
should inject those values and load secret values from Secrets Manager. Do not set
`AWS_ENDPOINT_URL` or static AWS access keys in staging.

The current Terraform foundation defines ECS task roles for the existing staging bucket:

- `core-api` can list and read/write `documents/*` and use the S3 KMS key.
- `ai-service` can list/read `documents/*` and decrypt the S3 KMS key.
- `web` and `audit-service` receive no S3 permissions.

Before initializing the main stack for the first time, bootstrap the remote state resources:

```bash
cd bootstrap
terraform init
terraform apply
cd ..
terraform init -migrate-state
```

The bootstrap stack creates the encrypted, versioned state bucket
`ekm-terraform-state-474632925730` and the pay-per-request DynamoDB lock table
`ekm-terraform-locks`. The existing staging S3 bucket must be imported into the main
stack before the first apply:

```bash
terraform import -var-file=../../../staging.tfvars.example aws_s3_bucket.documents ekm-documents-staging
```

The staging SQS configuration creates these contract queues and DLQs:

- `ekm-staging-document-ingest` / `ekm-staging-document-ingest-dlq` — 300-second visibility timeout
- `ekm-staging-document-status` / `ekm-staging-document-status-dlq` — 60-second visibility timeout
- `ekm-staging-audit-events` / `ekm-staging-audit-events-dlq` — 60-second visibility timeout

All queues use SQS-managed server-side encryption, 20-second long polling, and a redrive policy
after three receives. The task-role policies grant only the send/receive permissions required by
each service.

The RDS configuration provisions PostgreSQL 16 (`db.t4g.micro`) in the two private subnets. The
DB subnet group spans both availability zones, while the staging instance remains single-AZ to
keep the requested instance size and staging cost profile. RDS manages the master password in
Secrets Manager. The RDS security group allows port 5432 only from the shared ECS task security
group. The existing core-api Flyway V1 migration runs `CREATE EXTENSION IF NOT EXISTS vector`
when it initializes the database.

Milestone 3 application secret containers are created as:

- `ekm/staging/jwt-secret`
- `ekm/staging/openai-api-key`
- `ekm/staging/internal-service-token`

Terraform creates the secret metadata and task-role read permissions, but never stores secret
values. Populate values through an approved secret-management workflow, for example:

```bash
aws secretsmanager put-secret-value \
  --secret-id ekm/staging/jwt-secret \
  --secret-string '<value supplied securely>' \
  --region us-west-2
```

The RDS-managed master credential is exposed through `rds_master_user_secret_arn`. ECS task
definitions will reference these ARNs when the Milestone 4 ECS configuration is added.

The monthly AWS budget is configured in `us-east-1` because AWS Budgets is a global billing
service. It defaults to USD 50 with 80% actual and 100% forecasted email alerts when
`budget_notification_email` is populated in local `staging.tfvars`. AWS Budgets does not track
OpenAI billing; OpenAI usage remains a manual/provider-side check.

Run from this directory:

```bash
cp ../../../staging.tfvars.example staging.tfvars
terraform init
terraform plan -var-file=staging.tfvars
```

The existing bucket must be referenced or imported into state before applying. The task
roles can later be attached to ECS task definitions in the Milestone 4 configuration.
