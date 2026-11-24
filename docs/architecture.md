# Architecture

```mermaid
flowchart LR
  U[Employee / Admin browser] -->|HTTPS + JWT| WEB[web<br/>Next.js :3000]
  WEB -->|REST + SSE /api/v1| API[core-api<br/>Spring Boot :8080]
  API -->|REST + SSE /internal/v1<br/>X-Internal-Token| AI[ai-service<br/>FastAPI :8000]
  API -->|put / presign| S3[(S3 documents<br/>SSE-KMS)]
  API -->|DOCUMENT_INGEST_REQUESTED| Q1[[SQS ekm-document-ingest]]
  Q1 --> W[ai-service worker]
  W -->|get| S3
  W -->|chunks + embeddings| PG[(PostgreSQL 16<br/>+ pgvector)]
  W -->|DOCUMENT_INGEST_STATUS| Q2[[SQS ekm-document-status]]
  Q2 --> API
  API -->|AUDIT_EVENT| Q3[[SQS ekm-audit-events]]
  Q3 --> AUD[audit-service<br/>Spring Boot :8081]
  AUD --> PG
  API --> PG
  AI -->|hybrid search, dept pre-filter| PG
  AI -->|embeddings + gpt-4o-mini| OAI[(OpenAI API)]
  subgraph AWS [AWS: ECS Fargate · CloudWatch · Secrets Manager]
    API
    AI
    W
    AUD
    WEB
  end
```

| Concern | Decision |
|---|---|
| Services | `web`, `core-api`, `audit-service`, `ai-service` (API + SQS worker) — see `contracts/ports-and-services.md` |
| Data | One PostgreSQL 16 database with pgvector; table ownership in `contracts/database/README.md` |
| Async | SQS for ingestion (heavy, slow) and audit (fire-and-forget). DLQ after 3 attempts |
| Security | JWT (HS256) issued by core-api; RBAC (EMPLOYEE/MANAGER/ADMIN); department isolation enforced in core-api **and** in the SQL of the retriever; ai-service internal-only |
| AI | text-embedding-3-small (1536-d), gpt-4o-mini; hybrid retrieval (cosine + Postgres FTS); grounded prompt with `[n]` citations; similarity threshold → "not found" → knowledge gap |
| Ops | Docker everywhere (templates in `manali_devops/docker`); Terraform; GitHub Actions (OIDC) + Jenkins → ECR → ECS Fargate (5 services incl. separate `ai-worker`); CloudWatch logs/metrics/alarms. Details: `manali_devops/docs/aws-architecture.md` |

See [integration-guide.md](integration-guide.md) for the end-to-end sequences.
