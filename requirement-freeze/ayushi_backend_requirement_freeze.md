# Backend & Security Requirement Freeze

**Enterprise Knowledge Management Platform**

| | |
|---|---|
| Project | Enterprise Knowledge Management Platform |
| Scope owner | Ayushi — Backend and Security |
| Freeze date | November 6, 2026 |
| Target demonstration | December 2, 2026 |

This document defines the Backend and Security (`core-api` and `audit-service`) MVP scope for the December 2, 2026 demonstration.

## Objective

Provide the system of record and the enterprise security boundary for the platform using Java 21 and Spring Boot 3.4 microservices (`core-api` on port `8080` and `audit-service` on port `8081`). `core-api` is the single entry point for the web frontend: it authenticates users via JWT, enforces Role-Based Access Control (RBAC) and strict department-level data isolation, manages document uploads to Amazon S3, coordinates asynchronous ingestion via Amazon SQS, proxies hybrid search and RAG chat requests to `ai-service` with server-computed `allowedDepartments`, stores conversations, and serves admin analytics. `audit-service` consumes audit events asynchronously to record the immutable audit trail, query logs, and knowledge gaps.

## In Scope

Priorities: **MUST** protects the end-to-end demo path, **SHOULD** is expected for completeness, **COULD** is dropped first if schedule pressure arises.

**Foundation & Contracts (M1 — Completed)**

- MUST: Apache Maven multi-module architecture (`common`, `core-api`, `audit-service`) targeting Java 21 and Spring Boot 3.4+.
- MUST: PostgreSQL + `pgvector` schema management via automated Flyway migrations (`V1__init_schema.sql`, `V2__contract_schema_m1_m3.sql`).
- MUST: Global `camelCase` JSON serialization, ISO-8601 UTC timestamps, and shared error response body (`code`, `message`, `details`, `traceId`, `timestamp`) via `@RestControllerAdvice`.
- MUST: `X-Request-Id` request correlation filter with SLF4J MDC (`traceId`, `userId`) and structured JSON logging.
- MUST: Live OpenAPI 3.0 / Swagger UI documentation (`/swagger-ui.html`, `/v3/api-docs`).

**Document Ingestion, S3 & SQS Pipeline (M2 — Completed)**

- MUST: Multipart upload (`POST /api/v1/documents` and `/api/v1/documents/upload`) validating PDF (`%PDF-`) and DOCX (`PK\x03\x04`) by magic bytes (`415`) and size ≤ 25 MB (`413`).
- MUST: SHA-256 file checksum calculation and raw document storage in S3 at `documents/{dept}/{documentId}/v{n}/{filename}` (SSE-KMS in AWS, plain in LocalStack).
- MUST: Transactional persistence of `documents` and `document_versions` (`status = QUEUED`) followed by publishing `DOCUMENT_INGEST_REQUESTED` to SQS queue `ekm-document-ingest`.
- MUST: Idempotent SQS consumer on `ekm-document-status` updating document/version lifecycle (`PROCESSING` → `INDEXED` / `FAILED`), `page_count`, `chunk_count`, `error_code`, `error_message`, `indexed_at`, and `current_version`.
- MUST: Department-scoped document listing (`GET /api/v1/documents`) with pagination and filtering (`department`, `category`, `status`, `q`), document detail (`GET /api/v1/documents/{id}`), and department reference endpoint (`GET /api/v1/departments`).
- SHOULD: Metadata update (`PATCH /api/v1/documents/{id}`), soft delete (`DELETE /api/v1/documents/{id}` marking chunks `is_deleted = true`), new version upload (`POST /api/v1/documents/{id}/versions`), and 5-minute presigned S3 download URLs (`GET /api/v1/documents/{id}/download-url` with `#page=N`).

**Hybrid Search, Chat Proxy & Citations (M3 — Completed)**

- MUST: `AiServiceClient` forwarding `X-Internal-Token` and `X-Request-Id` to `ai-service` with timeouts (10 s search, 60 s answer) and single retry on `502`/`503`.
- MUST: Hybrid search endpoint (`POST /api/v1/search`) intersecting any requested departments with the caller's server-computed `allowedDepartments`.
- MUST: Non-streaming RAG chat endpoint (`POST /api/v1/chat`) managing conversations, passing the last 6 messages as history, and persisting `USER` and `ASSISTANT` messages with structured `citations`.
- MUST: Citation chunk preview endpoint (`GET /api/v1/documents/{id}/chunks/{chunkId}`) enforcing department isolation (`404` if unauthorized).
- SHOULD: Conversation history endpoints (`GET /api/v1/conversations`, `GET /api/v1/conversations/{id}`, `DELETE /api/v1/conversations/{id}`).

**Authentication, RBAC, Isolation & Streaming Chat (M4)**

- MUST: `POST /api/v1/auth/login` with BCrypt password verification, inactive-user rejection (`401`), and HS256 JWT issuance (`iss`, `sub`, `email`, `name`, `role`, `department`, `iat`, `exp`).
- MUST: `GET /api/v1/users/me` returning the authenticated user's profile, `allowedDepartments`, and UI `permissions`.
- MUST: Role-Based Access Control (`EMPLOYEE` read/query own dept + `GENERAL`; `MANAGER` write own dept; `ADMIN` all departments + `/api/v1/admin/**`).
- MUST: Strict department isolation across all document, chunk, search, and chat routes (returning `404` on cross-department resource access and emitting `ACCESS_DENIED` audit events), verified by automated RBAC leakage tests.
- MUST: Server-Sent Events (SSE) chat relay (`POST /api/v1/chat/stream`) emitting `meta` → relayed `token` / `citations` / `done` events and persisting the final assistant message.
- SHOULD: Configurable CORS (`CORS_ALLOWED_ORIGINS`) and per-user chat rate limiting (`429`).
- COULD: Admin user management endpoints (`GET / POST / PATCH /api/v1/admin/users`).

**Audit Service & Admin Analytics (M5)**

- MUST: Asynchronous `AUDIT_EVENT` publisher in `core-api` writing to SQS queue `ekm-audit-events` without blocking user requests.
- MUST: `audit-service` SQS consumer persisting idempotent `audit_logs`, `query_logs` (on `QUERY_ASKED`), and upserting `knowledge_gaps` on unanswered queries (`answered = false`).
- MUST: Admin analytics endpoints (`/api/v1/admin/analytics/summary`, `queries-over-time`, `top-queries`, `top-documents`), knowledge-gaps list/update (`/api/v1/admin/knowledge-gaps`), and audit log viewer (`/api/v1/admin/audit-logs`).
- SHOULD: Answer feedback endpoint (`POST /api/v1/chat/messages/{id}/feedback`).

**Integration, Verification & Packaging (M6)**

- MUST: Multi-stage non-root Dockerfiles (`eclipse-temurin:21-jre-alpine`) with `/actuator/health` health checks for both `core-api` and `audit-service`.
- MUST: End-to-end staging verification with `web` and `ai-service`, OWASP Top-10 security checklist, and `.http`/Postman collection for the final demo.

## Out of Scope

- Text extraction from PDF/DOCX, tokenization, chunking strategies, OpenAI embedding generation, pgvector similarity SQL execution, and LLM prompt synthesis (owned by `ai-service` / Vedanti).
- Next.js UI components, client-side state management, and browser rendering (owned by Frontend / Changhyun).
- Terraform provisioning, ECS/Fargate cluster administration, Jenkins/GitHub Actions runner infrastructure, and CloudWatch alarm setup (owned by DevOps / Manali).
- OAuth2/OIDC SSO providers (Okta, Azure AD), multi-factor authentication (MFA), or refresh-token rotation beyond standard JWT bearer authentication.
- File formats other than PDF and DOCX, virus scanning appliances, or real-time WebSocket collaborative editing.

## Acceptance Criteria

- [x] Multi-module Maven build (`common`, `core-api`, `audit-service`) compiles cleanly and passes automated unit/integration tests (`./mvnw test`).
- [x] Flyway migrations automatically initialize PostgreSQL tables and default department/admin seed data.
- [x] Document upload validates PDF/DOCX magic bytes and 25 MB size limits, uploads to S3, records version metadata, and emits `DOCUMENT_INGEST_REQUESTED` to SQS.
- [x] SQS status consumer idempotently updates document status (`PROCESSING` → `INDEXED` / `FAILED`), page/chunk counts, and active version pointers.
- [x] Search (`POST /api/v1/search`), non-streaming chat (`POST /api/v1/chat`), conversations (`GET/DELETE /api/v1/conversations`), and chunk preview (`GET /api/v1/documents/{id}/chunks/{chunkId}`) enforce `allowedDepartments` isolation.
- [ ] JWT login (`POST /api/v1/auth/login`), `/api/v1/users/me`, and Spring Security RBAC guards enforce role and department permissions with zero cross-department leakage on the golden evaluation matrix.
- [ ] Streaming chat (`POST /api/v1/chat/stream`) relays SSE events (`meta`, `token`, `citations`, `done`) to the browser and persists the completed answer.
- [ ] `audit-service` consumes `ekm-audit-events` into `audit_logs`, `query_logs`, and `knowledge_gaps`, and `core-api` serves the `/api/v1/admin/**` analytics endpoints.
- [ ] Both backend services run in Docker Compose and AWS ECS/Fargate with passing `/actuator/health` checks and structured JSON logs.

## Inputs the Backend Provides to DevOps & Team

| Required input | Backend status |
|---|---|
| Source code | `ayushi_backend/` (`common`, `core-api`, `audit-service`) — Milestones 1–3 complete and tested |
| Dockerfiles | `ayushi_backend/core-api/Dockerfile` and `ayushi_backend/audit-service/Dockerfile` (multi-stage, non-root `ekm` user, `HEALTHCHECK`) |
| Ports and health endpoints | `core-api`: port `8080`, `GET /actuator/health`; `audit-service`: port `8081`, `GET /actuator/health` |
| Startup and test commands | Build: `cd ayushi_backend && ./mvnw clean package -DskipTests`; Test: `cd ayushi_backend && ./mvnw test` |
| Environment variables | Uses only standard names from `.env.example` (`POSTGRES_*`, `AWS_*`, `S3_DOCUMENTS_BUCKET`, `SQS_*`, `JWT_*`, `INTERNAL_SERVICE_TOKEN`, `AI_SERVICE_URL`, `CORS_ALLOWED_ORIGINS`) |
| External dependencies | PostgreSQL 16 + `pgvector`, S3 bucket (`ekm-documents-local` / AWS S3), 3 SQS queues (`ekm-document-ingest`, `ekm-document-status`, `ekm-audit-events`), and `ai-service` (`:8000`) |

## Dependencies From Service Owners

| Owner | Required input | Needed by |
|---|---|---|
| Vedanti (AI/RAG) | `POST /internal/v1/search` and `POST /internal/v1/answer` live endpoints + `DOCUMENT_INGEST_STATUS` SQS events + `document_chunks` writes | M3 end |
| Vedanti (AI/RAG) | `POST /internal/v1/answer/stream` SSE stream (`token`, `citations`, `done` / `error`) | M4 |
| Changhyun (Frontend) | Multipart upload payload matching `POST /api/v1/documents`, Bearer JWT header on protected routes, SSE stream reader on `/api/v1/chat/stream` | M2–M4 |
| Manali (DevOps) | LocalStack S3 + SQS queues (`make up`), RDS PostgreSQL 16 + `pgvector`, ECS task definitions, and Secrets Manager injection for `JWT_SECRET` and `INTERNAL_SERVICE_TOKEN` | Local now; AWS by M4–M6 |

## Freeze Rule

New API endpoints, database tables, queue schemas, or authentication mechanisms outside `contracts/` require team agreement, a contract pull request updating `contracts/CHANGELOG.md`, and must not delay the agreed MVP. If schedule pressure arises, **COULD** items (such as admin user-management CRUD) are cut first so security isolation, RAG chat, ingestion, and analytics remain demo-ready.

## Decision

This scope is the baseline for Backend & Security implementation and team integration. Frontend, AI/RAG, and DevOps services can rely on the frozen OpenAPI (`contracts/openapi/core-api.yaml`), event (`contracts/events/`), database (`contracts/database/migrations/`), and auth (`contracts/auth.md`) contracts defined here.

