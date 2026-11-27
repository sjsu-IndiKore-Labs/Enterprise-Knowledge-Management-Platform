# TODO — Manali (DevOps & Cloud, Team Lead)

> Agents: read [`/AGENTS.md`](../AGENTS.md) first. This file is Manali's plan; other agents read it for context.
> Tick `[x]` when done, add items you discover, and keep "Blocked / waiting on" current.

## Your role in one paragraph
You make everyone else's code **run together** — locally (docker-compose + LocalStack) and in AWS (ECS Fargate, RDS Postgres
with pgvector, S3 with KMS, SQS with DLQs, CloudWatch, Secrets Manager) — and you guard integration: contracts, CI that
validates contracts and builds every service, CD to staging, monitoring/alarms, load tests. As team lead you also run the
weekly sync, keep `docs/timeline.md` current, and own the final demo + slide deck.

## You PROVIDE
| To | What |
|---|---|
| Everyone | `docker-compose.yml`, `make up / up-apps / seed / mock-* / lint-contracts / corpus / ingest-corpus`, `.env.example`, LocalStack S3 + SQS |
| Everyone | CI on every PR (contracts lint + each service's build/test), CD to staging on `main` |
| Ayushi, Vedanti | AWS: RDS Postgres 16 + pgvector, S3 bucket (SSE-KMS), 3 SQS queues + DLQs, IAM task roles, Secrets Manager (`JWT_SECRET`, `OPENAI_API_KEY`, `INTERNAL_SERVICE_TOKEN`, DB password) |
| Changhyun | Public ALB URL for web + core-api, HTTPS (ACM) if a domain is available |

## You CONSUME
Each service's `Dockerfile` + health endpoint (see `contracts/ports-and-services.md`). Until they exist, compose runs infra only (`apps` profile).

## Milestone 1 — catch-up (was due Oct 11) — finish by **Oct 21**
- [x] Repo structure, `AGENTS.md`, contracts v1, member TODOs, questions mailbox, sample data (2026-10-19)
- [ ] Get every member to acknowledge `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`
- [ ] Protect `main` and `develop` (PR required, 1 approval, CI green); set `develop` as default PR target
- [ ] Verify `make up` on Mac + Windows (WSL2): postgres+pgvector healthy, LocalStack creates bucket + 6 queues, `make seed` works
- [x] Docker base templates for Java (Temurin 21, multi-stage), Node 20 (Next standalone), Python 3.11-slim — add to `docs/` or each owner's folder via PR they approve
- [x] CI (`.github/workflows/ci.yml`): contracts lint + event examples validation; per-service jobs that run only when the service exists

## Milestone 2 — S3, SQS, compose (Oct 12 – Oct 25)
- [x] LocalStack init script final (bucket, CORS for presigned URLs, queues with redrive policy maxReceiveCount=3, visibility 300 s on ingest)
- [x] Terraform skeleton (`infra/terraform`): remote state (S3 + DynamoDB lock), VPC (2 AZ, public/private subnets), ECR repos ×4
- [x] Terraform: S3 bucket with **KMS (SSE-KMS)**, block public access, versioning; SQS queues + DLQs; IAM policies (core-api: S3 put/get + SQS send ingest/audit + receive status; ai-service: S3 get + SQS receive ingest + send status; audit-service: SQS receive audit)
- [x] `docker-compose.yml` `apps` profile works as soon as the first Dockerfile lands

## Milestone 3 — Database in AWS (Oct 26 – Nov 8)
- [x] RDS PostgreSQL 16 (db.t4g.micro, free-tier) with `CREATE EXTENSION vector` (RDS supports pgvector), private subnet, SG only from ECS tasks
- [x] Secrets Manager entries + task definitions reading them; budget alarm on OpenAI usage (manual check) and AWS Budgets ($ threshold)

## Milestone 4 — CI/CD to ECS (Nov 9 – Nov 20)
- [x] ECS cluster (Fargate), 4 services + task definitions (CPU/memory sized), ALB with path routing (`/api/*` → core-api, `/*` → web); ai-service & audit-service internal only (Service Connect / Cloud Map)
- [x] GitHub Actions CD on `main`: build → push to ECR (tag = git SHA) → `aws ecs update-service` → wait stable → smoke test
- [x] Jenkins pipeline (`jenkins/Jenkinsfile`) doing the same stages (course requirement); Jenkins in Docker locally or on EC2
- [x] OIDC GitHub → AWS role (no long-lived keys in GitHub secrets)

## Milestone 5 — Observability (Nov 21 – Nov 28)
- [x] CloudWatch log groups per service (awslogs driver, 14-day retention), JSON logs searchable by `traceId`
- [x] Metrics + alarms: ECS CPU/memory, ALB 5xx & p95 latency, SQS `ApproximateAgeOfOldestMessage` on ingest, **DLQ depth > 0**, RDS CPU/storage; SNS email
- [x] CloudWatch dashboard (screenshot for the report)
- [ ] k6 load test (`load-test/chat.js`) with `resources/load-test/queries.csv`: 20 virtual users × 5 min, record p95 latency and error rate

## Milestone 6 — Integration, staging, demo (Nov 29 – Dec 2)
- [ ] Full staging deploy; `make ingest-corpus API=<staging url>`; run golden eval on staging
- [ ] End-to-end test checklist with all four members (`docs/demo-script.md`)
- [ ] Final slide deck + architecture diagram + project documentation; rehearse demo twice; record a backup video
- [ ] Tear-down plan for AWS resources after grading (cost)

## Written on 2026-10-19, not yet run against a real AWS account
- [ ] Bootstrap state + first `terraform apply` of staging ([docs/aws-deployment.md](docs/aws-deployment.md)); set the OpenAI secret
- [ ] GitHub variables `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `STAGING_URL` + environment `staging`; first green CD run
- [ ] Jenkins running (local Docker or EC2) with the pipeline; screenshot for the report
- [ ] Get answers to the container-convention questions sent to Ayushi, Vedanti and Changhyun
- [x] `REAL=1 make test-docker` once each service has code (2026-11-27: all 5 real images healthy against fresh Postgres + LocalStack; CI runs it with `BACKEND_NETWORK=ekm_default`)
- [x] Backend switched to Maven: build-push.sh / test_images.sh detect `ayushi_backend/pom.xml`, fixture converted to Maven
- [ ] Retire `make migrate-raw` once Ayushi confirms Flyway is the schema source of truth (local DBs built with it break core-api's V2 migration)

## Team-lead recurring
- [ ] Weekly sync (suggest Sunday): each member reports TODO progress + open questions; update `docs/timeline.md`
- [ ] Every Friday: `make questions` — chase unanswered `blocking` questions
- [ ] Merge `develop` → `main` at the end of each milestone (tag `m2`, `m3`, …)

## Blocked / waiting on
- ai-service/ai-worker can't reach RDS until they read `POSTGRES_*`: [questions/to-vedanti/2026-11-27_from-manali_postgres-env-var-names.md](../questions/to-vedanti/2026-11-27_from-manali_postgres-env-var-names.md) (blocking)
- Flyway vs contract schema (`audit_logs.metadata` TEXT vs JSONB): [questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md](../questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md)
