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
- [x] Protect `main` and `develop` (PR required, 1 approval, CI green); set `develop` as default PR target (verified 2026-11-27)
- [ ] Verify `make up` on Mac + Windows (WSL2): postgres+pgvector healthy, LocalStack creates bucket + 6 queues, `make seed` works
  - Mac ✔ 2026-11-27: full `--profile apps` stack on fresh volumes, `validate-localstack` OK (bucket, CORS, 3 queues + 3 DLQs). Windows/WSL2 still to do
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
  - Local baseline 2026-11-27 (Mac, `AI_PROVIDER=fake`, 20 VUs × 5 min): 2976 req, p95 answered chat 36 ms, 79% rate-limited (429, expected: 6 users × 20/min), 3.5% errors = admin chats 502 (`OPERATIONS` dept, see Ayushi question). `chat.js` now counts 429 separately. **Still to run on staging with OpenAI**

## Milestone 6 — Integration, staging, demo (Nov 29 – Dec 2)
- [ ] Full staging deploy; `make ingest-corpus API=<staging url>`; run golden eval on staging
- [ ] End-to-end test checklist with all four members (`docs/demo-script.md`)
  - Automated: `python manali_devops/tests/smoke/demo_check.py --api <url>` walks the demo script (upload → INDEXED, DOCX, search/chat/stream, versioning, RBAC, admin)
  - 2026-11-27 local run found 4 core-api bugs → [question to Ayushi](../questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md); fixed the h2c/uvicorn one in the Python template (`--http h11`)
- [ ] Final slide deck + architecture diagram + project documentation; rehearse demo twice; record a backup video
- [x] Tear-down plan for AWS resources after grading (cost): [docs/aws-deployment.md §9](docs/aws-deployment.md#9-teardown-after-grading)

## Written on 2026-10-19, not yet run against a real AWS account
- [ ] Bootstrap state + first `terraform apply` of staging ([docs/aws-deployment.md](docs/aws-deployment.md)); set the OpenAI secret
- [ ] GitHub variables `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `STAGING_URL` + environment `staging`; first green CD run
- [ ] Jenkins running (local Docker or EC2) with the pipeline; screenshot for the report
- [ ] Get answers to the container-convention questions sent to Ayushi, Vedanti and Changhyun
- [x] `REAL=1 make test-docker` once each service has code (2026-11-27: all 5 real images healthy against fresh Postgres + LocalStack; CI runs it with `BACKEND_NETWORK=ekm_default`)
- [x] Backend switched to Maven: build-push.sh / test_images.sh detect `ayushi_backend/pom.xml`, fixture converted to Maven
- [x] ai-service/ai-worker read `POSTGRES_*` + `POSTGRES_SSLMODE` (fixed by Vedanti in #41; images re-tested healthy 2026-11-27). Close [the question](../questions/to-vedanti/2026-11-27_from-manali_postgres-env-var-names.md) once she fills in the Answer
- [ ] Retire `make migrate-raw` once Ayushi confirms Flyway is the schema source of truth (local DBs built with it break core-api's V2 migration)

## Team-lead recurring
- [ ] Weekly sync (suggest Sunday): each member reports TODO progress + open questions; update `docs/timeline.md`
- [ ] Every Friday: `make questions` — chase unanswered `blocking` questions
- [ ] Merge `develop` → `main` at the end of each milestone (tag `m2`, `m3`, …)

## Blocked / waiting on
- **Blocking demo:** core-api fresh-DB schema lacks `document_chunks.embedding`, INDEXED events lost (@Transactional self-invocation), DOCX upload 500: [questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md](../questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md)
- Staging deploy, CD, k6 on staging, Jenkins screenshot: need `aws login` + GitHub variables (see below)
