# DevOps Handoff — Milestones 1–6

Owner: Manali (DevOps & Cloud)

This document describes the infrastructure Manali has provided for Ayushi, Vedanti, and
Changhyun across the project milestones. It contains no AWS credentials or application
secrets.

## Current milestone status — 2026-11-18

### Milestone 1 — Foundation: mostly completed

- Completed: repository structure, contracts v1, sample data, branch protection, Docker base templates, local validation, and CI contract/backend build checks.
- Remaining: architecture acknowledgement from Ayushi, Changhyun, and Vedanti; Windows/WSL2 validation; and full application startup validation.

### Milestone 2 — S3, SQS, and Compose: infrastructure completed; application integration pending

- Completed: LocalStack S3/SQS/DLQ initialization, Terraform state/VPC/ECR foundation, staging S3 and SQS resources, encryption, CORS, IAM task policies, and DLQ redrive settings.
- Pending: the Compose `apps` profile must be validated with the backend images after core-api runtime settings are aligned.

### Milestone 3 — AWS database, secrets, and budgets: infrastructure foundation completed; verification and runtime wiring pending

- Completed: private RDS PostgreSQL 16 foundation, two-AZ DB subnet group, ECS-only RDS security group, RDS-managed master secret, application secret containers, task-role read permissions, and AWS Budget Terraform configuration.
- In progress: pgvector verification is prepared in [`manali_devops/scripts/verify-pgvector.sh`](../../manali_devops/scripts/verify-pgvector.sh) and is waiting for core-api/Flyway V1 to initialize the authoritative schema.
- Not started: populate application secret values, complete ECS secret injection validation, and perform the manual OpenAI usage/budget check.

### Milestone 4 — ECS and CI/CD: foundation implemented; deployment inputs and delivery automation pending

- In progress: ECS/Fargate cluster, four task-definition/service foundations, ALB routing, Cloud Map names, ECR repositories, CloudWatch log groups, and secret-injection structure are implemented in Terraform.
- Pending: service images and runtime inputs from the service owners, GitHub Actions deployment to ECR/ECS, stable-service smoke tests, and GitHub OIDC.

### Milestone 5 — Observability: not started

- Remaining: CloudWatch metrics and alarms, SNS notifications, dashboard, and the k6 chat load test.

### Milestone 6 — Integration and demo: not started

- Remaining: full staging deployment, corpus ingestion, golden evaluation, end-to-end demo checklist, final documentation/slides, rehearsal, backup video, and AWS teardown plan.

## Open dependency tasks and question routing

Dependencies are recorded in the questions mailbox so the owner can answer them in place. Do not duplicate these as new questions unless the scope changes.

### Ayushi — `questions/to-ayushi/`

- Align core-api with the DevOps runtime contract: port `8080`, PostgreSQL host/database settings, non-root runtime images, and `/actuator/health`.
- Confirm the authoritative Flyway migration and apply it so the pgvector verifier can confirm V1, `document_chunks.embedding` as `vector(1536)`, and the HNSW cosine index.
- Track in [`2026-11-06_from-manali_backend-runtime-integration.md`](../../questions/to-ayushi/2026-11-06_from-manali_backend-runtime-integration.md).

### Changhyun — `questions/to-changhyun/`

- Provide the web Dockerfile using the Node 20/Next standalone template.
- Confirm the startup command, port `3000`, `/api/health`, `NEXT_PUBLIC_API_BASE_URL` handling, and any additional runtime variables required by ECS.
- Track in [`2026-11-13_from-manali_ecs-runtime-inputs.md`](../../questions/to-changhyun/2026-11-13_from-manali_ecs-runtime-inputs.md).

### Vedanti — `questions/to-vedanti/`

- Provide the AI-service Dockerfile using the Python 3.11 template.
- Confirm the startup command, port `8000`, `/health`, `AI_PROVIDER=openai` for staging, and any additional runtime variables required by ECS.
- Track in [`2026-11-13_from-manali_ecs-runtime-inputs.md`](../../questions/to-vedanti/2026-11-13_from-manali_ecs-runtime-inputs.md).

### All members — `questions/to-all/`

- Acknowledge the architecture and contracts v1 decision before relying on the shared interfaces.
- Track in [`2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`](../../questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md).

## What Manali has completed

### Shared repository and CI

- Contracts, OpenAPI specifications, event schemas, database migrations, ports, and environment names are available under `contracts/`.
- CI validates contracts and builds the backend, frontend, and AI service jobs.
- Jenkins backend build uses the Maven wrapper:
  `cd ayushi_backend && ./mvnw clean package -DskipTests`.
- Branch protection is configured for `main` and `develop`; pull requests merge into `develop` first.

### Local development

- `docker-compose.yml` provides PostgreSQL 16 with pgvector and LocalStack.
- `make up` starts the local infrastructure.
- `make seed` loads the development users/departments.
- LocalStack creates:
  - S3 bucket `ekm-documents-local`
  - CORS for local web development
  - `ekm-document-ingest` and `ekm-document-ingest-dlq`
  - `ekm-document-status` and `ekm-document-status-dlq`
  - `ekm-audit-events` and `ekm-audit-events-dlq`
- SQS settings:
  - ingest visibility timeout: 300 seconds
  - status/audit visibility timeout: 60 seconds
  - long polling: 20 seconds
  - redrive after 3 receives
- `make validate-localstack` verifies the LocalStack bucket, CORS, queues, DLQs, visibility, and redrive policies.
- Prism mocks are available through `make mock-core-api` on port `4010` and `make mock-ai-service` on port `4011`.

### Runtime and configuration

- The service ports are defined in `contracts/ports-and-services.md`:
  - web: `3000`
  - core-api: `8080`
  - audit-service: `8081`
  - ai-service: `8000`
- `.env.example` is the source of truth for environment variable names.
- `staging.env.example` contains non-secret AWS staging runtime values.
- LocalStack uses `AWS_ENDPOINT_URL=http://localhost:4566` and local resource names.
- AWS staging leaves `AWS_ENDPOINT_URL` empty and uses ECS IAM task roles instead of static AWS keys.

### AWS staging infrastructure

- Remote Terraform state:
  - S3 bucket: `ekm-terraform-state-474632925730`
  - DynamoDB lock table: `ekm-terraform-locks`
- VPC: `vpc-090b6120a9d493166`
  - two public subnets across `us-west-2a` and `us-west-2b`
  - two private subnets across `us-west-2a` and `us-west-2b`
  - NAT gateway for private-subnet egress
- ECR repositories:
  - `ekm-staging-core-api`
  - `ekm-staging-audit-service`
  - `ekm-staging-ai-service`
  - `ekm-staging-web`
- Existing S3 bucket `ekm-documents-staging` is imported into Terraform and managed with:
  - SSE-KMS
  - S3 Bucket Key
  - versioning
  - CORS
  - ownership controls
  - block public access
- AWS queues:
  - `ekm-staging-document-ingest` and `-dlq`
  - `ekm-staging-document-status` and `-dlq`
  - `ekm-staging-audit-events` and `-dlq`
- AWS IAM task roles provide least-privilege access:
  - core-api: S3 read/write and SQS publish/consume as required
  - ai-service: S3 read and SQS ingest/status access
  - audit-service: SQS audit-event consume access
- AWS RDS PostgreSQL 16.13 is available privately at the Terraform output endpoint.
  - instance class: `db.t4g.micro`
  - database subnet group spans two private AZs
  - port `5432` is allowed only from the ECS task security group
  - master password is managed by RDS in Secrets Manager
  - core-api Flyway V1 contains `CREATE EXTENSION IF NOT EXISTS vector`

## How to work locally

From the repository root:

```bash
cp .env.example .env
make up
make seed
make validate-localstack
```

Use `AI_PROVIDER=fake` when an OpenAI key is not available. Use Prism mocks while a
dependent service is still being implemented.

Do not put AWS credentials, JWT secrets, database passwords, or OpenAI keys in Git.

## What is available for Ayushi

### Available now

- Java 21 / Spring Boot Maven build and Maven wrapper
- core-api port `8080` and audit-service port `8081`
- PostgreSQL/pgvector and Flyway migration path
- LocalStack S3 and SQS
- AWS staging S3, SQS, RDS, and IAM task-role configuration
- OpenAPI, event, database, port, and environment contracts
- Backend Dockerfiles and health endpoints for the existing services

## What is available for Vedanti

### Available now

- PostgreSQL 16 with pgvector locally
- LocalStack S3 and the ingest/status queues
- Contracted ingest/status event schemas and examples
- S3 read access and SQS permissions defined for the AWS AI task role
- `AI_PROVIDER=fake` configuration path for offline development
- `document_chunks` schema with `vector(1536)` embeddings

## What is available for Changhyun

### Available now

- Core API OpenAPI contract
- Prism core-api mock on `http://localhost:4010`
- Local web port `3000`
- Contracted upload, document, status, error, and future SSE behavior
- Sample documents and edge cases under `resources/`
- `NEXT_PUBLIC_API_BASE_URL` configuration

## Important staging configuration note

The staging S3 CORS example currently allows `http://localhost:3000` for development.
Update `s3_allowed_origins` in a local, uncommitted `staging.tfvars` with the real staging
web origin once the ALB/domain is available, then run Terraform plan before applying.
