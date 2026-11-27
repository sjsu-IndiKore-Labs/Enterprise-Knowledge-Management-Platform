# EKM system architecture

**Aliases:** Enterprise Knowledge Management Platform, EKM, Helios Knowledge Portal  
**Environment:** staging architecture; local development uses Docker Compose and LocalStack  
**Last verified:** 2026-11-23

## Purpose

EKM is a secure internal knowledge portal. Employees upload PDF and DOCX documents. The platform stores source files in S3, processes them through an ingestion worker, stores chunks and embeddings in PostgreSQL with pgvector, and answers questions using only documents the caller is allowed to see.

## Request and ingestion flows

```text
Browser
  │ HTTP/SSE
  ▼
web ───────────────► core-api ───────────────► ai-service
                       │  ▲                       │
                       │  │                       │
                       ▼  │                       ▼
                 S3 documents              PostgreSQL/pgvector
                       │
                       ▼
                 document-ingest SQS
                       │
                       ▼
                   ai-worker
                       │
                       ├── reads source document from S3
                       ├── extracts and chunks content
                       ├── creates embeddings
                       ├── writes chunks to PostgreSQL/pgvector
                       └── sends document status to document-status SQS

core-api ── audit-events SQS ──► audit-service ──► PostgreSQL
```

The browser communicates with `core-api`; it does not call S3, `ai-service`, or `ai-worker` directly.

## Service-to-service endpoints

| From | To | Protocol or resource | Purpose |
|---|---|---|---|
| Browser | `web` | HTTP | Load the portal |
| Browser | `core-api` | HTTP and SSE | Login, documents, search, chat, citations |
| `core-api` | `ai-service` | HTTP/SSE; `http://ai-service:8000` in ECS | Search and answer generation |
| `core-api` | `document-ingest` | SQS | Request document processing |
| `ai-worker` | S3 | `GetObject` | Read uploaded document bytes |
| `ai-worker` | PostgreSQL/pgvector | PostgreSQL | Store chunks and vectors |
| `ai-worker` | `document-status` | SQS | Report processing status |
| `core-api` | `audit-events` | SQS | Publish audit events |
| `audit-service` | PostgreSQL | PostgreSQL | Persist audit and knowledge-gap data |

## AWS staging resources

- ECS/Fargate cluster: `ekm-staging`
- Public entry point: application load balancer
- S3 bucket: `ekm-documents-staging-<accountId>`
- SQS queues: document ingest, document status, and audit events, each with a DLQ
- RDS: PostgreSQL 16 with pgvector support
- ECR repositories: `web`, `core-api`, `audit-service`, and `ai-service`
- Secrets Manager: JWT secret, internal service token, OpenAI key, and PostgreSQL password

## Security boundaries

- Department filtering is decided by `core-api` from the authenticated JWT.
- S3 objects are private and encrypted with SSE-KMS.
- `core-api` writes document objects under `documents/*`.
- `ai-service` and `ai-worker` have read-only access to `documents/*` and KMS decrypt access.
- The web portal and audit service do not receive document S3 permissions.

## Source references

- `../../../contracts/ports-and-services.md`
- `../../../contracts/openapi/core-api.yaml`
- `../../infra/terraform/ecs.tf`
- `../../infra/terraform/iam.tf`
- `../../infra/terraform/s3.tf`
- `../../infra/terraform/sqs.tf`
