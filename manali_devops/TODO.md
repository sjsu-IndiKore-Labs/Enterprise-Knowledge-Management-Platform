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
- [ ] Get every member to acknowledge `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md` — awaiting Ayushi, Changhyun, and Vedanti responses
- [x] Protect `main` and `develop` (PR required, 1 approval, CI green; stale reviews dismissed; force-push/deletion disabled; admins enforced) and set `develop` as the repository default branch — configured 2026-11-06.
- [ ] Verify `make up` on Mac + Windows (WSL2): macOS infrastructure passed (`postgres`, `localstack`, S3 bucket, 6 queues, and `make seed` on 2026-11-06); Windows/WSL2 and full app startup remain pending because `vedanti_ai/Dockerfile` and `changhyun_frontend/Dockerfile` are not present
- [x] Docker base templates for Java (Temurin 21, multi-stage), Node 20 (Next standalone), Python 3.11-slim — documented in `manali_devops/docs/docker-templates.md` (2026-11-06)
- [x] Cross-platform local validation command and Mac/Linux/WSL2 checklist — `make validate-local` and `manali_devops/docs/local-validation.md` (2026-11-06)
- [x] CI (`.github/workflows/ci.yml`): contracts lint + event examples validation; backend job detects the Maven parent POM, uses Java 21/Maven caching, and builds with the Maven wrapper (2026-11-06)

## Milestone 2 — S3, SQS, compose (Oct 12 – Oct 25)
- [x] LocalStack init script finalized and validated: bucket, CORS, 3 queues + 3 DLQs, redrive policy maxReceiveCount=3, visibility 300 s on ingest (2026-11-06)
- [x] Terraform foundation: encrypted remote state in S3 with DynamoDB locking, VPC across 2 AZs with public/private subnets and NAT egress, and four ECR repositories — applied to staging on 2026-11-06
- [x] Terraform: existing staging S3 bucket imported and managed with SSE-KMS, bucket keys, versioning, CORS, ownership controls, and block-public-access settings; SQS/DLQs and task-role policies applied to staging on 2026-11-06
- [ ] `docker-compose.yml` `apps` profile works with the new backend images after Ayushi resolves the core-api port/database configuration

## Milestone 3 — Database in AWS (Oct 26 – Nov 8)
- [ ] RDS PostgreSQL 16 (db.t4g.micro, free-tier) with `CREATE EXTENSION vector` (RDS supports pgvector), private subnet, SG only from ECS tasks
- [ ] Secrets Manager entries + task definitions reading them; budget alarm on OpenAI usage (manual check) and AWS Budgets ($ threshold)

## Milestone 4 — CI/CD to ECS (Nov 9 – Nov 20)
- [ ] ECS cluster (Fargate), 4 services + task definitions (CPU/memory sized), ALB with path routing (`/api/*` → core-api, `/*` → web); ai-service & audit-service internal only (Service Connect / Cloud Map)
- [ ] GitHub Actions CD on `main`: build → push to ECR (tag = git SHA) → `aws ecs update-service` → wait stable → smoke test
- [x] Jenkins pipeline (`jenkins/Jenkinsfile`) backend stage migrated from Gradle to Maven (2026-11-06); Jenkins in Docker locally or on EC2 remains
- [ ] OIDC GitHub → AWS role (no long-lived keys in GitHub secrets)

## Milestone 5 — Observability (Nov 21 – Nov 28)
- [ ] CloudWatch log groups per service (awslogs driver, 14-day retention), JSON logs searchable by `traceId`
- [ ] Metrics + alarms: ECS CPU/memory, ALB 5xx & p95 latency, SQS `ApproximateAgeOfOldestMessage` on ingest, **DLQ depth > 0**, RDS CPU/storage; SNS email
- [ ] CloudWatch dashboard (screenshot for the report)
- [ ] k6 load test (`load-test/chat.js`) with `resources/load-test/queries.csv`: 20 virtual users × 5 min, record p95 latency and error rate

## Milestone 6 — Integration, staging, demo (Nov 29 – Dec 2)
- [ ] Full staging deploy; `make ingest-corpus API=<staging url>`; run golden eval on staging
- [ ] End-to-end test checklist with all four members (`docs/demo-script.md`)
- [ ] Final slide deck + architecture diagram + project documentation; rehearse demo twice; record a backup video
- [ ] Tear-down plan for AWS resources after grading (cost)

## Team-lead recurring
- [ ] Weekly sync (suggest Sunday): each member reports TODO progress + open questions; update `docs/timeline.md`
- [ ] Every Friday: `make questions` — chase unanswered `blocking` questions
- [ ] Merge `develop` → `main` at the end of each milestone (tag `m2`, `m3`, …)

## Blocked / waiting on
- [ ] Ayushi: align core-api with the DevOps runtime contract (port 8080, Docker database hostname/config, non-root + healthcheck Docker runtime, and one authoritative Flyway migration). See [`questions/to-ayushi/2026-11-06_from-manali_backend-runtime-integration.md`](../questions/to-ayushi/2026-11-06_from-manali_backend-runtime-integration.md).
