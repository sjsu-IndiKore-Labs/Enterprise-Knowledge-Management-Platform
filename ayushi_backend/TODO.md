# TODO — Ayushi (Backend & Security)

> Agents: read [`/AGENTS.md`](../AGENTS.md) first. This file is Ayushi's plan; other agents read it for context.
> Tick `[x]` when done, add items you discover, and keep "Blocked / waiting on" current.

## Your role in one paragraph
You build the **system of record and the security boundary**. `core-api` is the only service the browser talks to:
it authenticates users (JWT), enforces RBAC + department isolation, stores files in S3, records documents/versions,
publishes ingestion jobs to SQS, consumes ingestion status, proxies search/chat to `ai-service` (passing the
`allowedDepartments` it computed from the JWT), stores conversations, emits audit events, and serves analytics.
`audit-service` consumes audit events and writes the audit trail, query logs and knowledge gaps. You also own the
**database schema** (Flyway migrations in `contracts/database/migrations`).

## You PROVIDE (others depend on these — keep them exactly as in contracts)
| To | What | Contract |
|---|---|---|
| Changhyun | Every `/api/v1/**` endpoint incl. SSE chat stream | `contracts/openapi/core-api.yaml`, `contracts/streaming.md` |
| Vedanti | `DOCUMENT_INGEST_REQUESTED` messages; `documents`/`document_versions` rows; DB schema incl. `document_chunks` | `contracts/events/`, `contracts/database/` |
| Manali | Dockerfiles for both services, `/actuator/health`, env vars only from `.env.example`, JSON logs | `contracts/ports-and-services.md` |

## You CONSUME
| From | What | Until it's ready, use |
|---|---|---|
| Vedanti | `POST /internal/v1/search`, `/answer`, `/answer/stream`; `DOCUMENT_INGEST_STATUS` messages | `make mock-ai-service` (Prism :4011) or `AI_PROVIDER=fake`; send example status messages from `contracts/events/examples/` with `awslocal` |
| Manali | Postgres+pgvector, LocalStack S3/SQS, CI pipeline, AWS (RDS, SQS, S3, ECS) | `make up` |

## Milestone 1 — catch-up (was due Oct 11) — finish by **Oct 21**
- [ ] Review `contracts/openapi/core-api.yaml`, `contracts/auth.md`, `contracts/database/migrations/V1__init_schema.sql`; acknowledge `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`
- [ ] Gradle multi-project skeleton (`common`, `core-api`, `audit-service`), Spring Boot 3.3+, Java 21, springdoc-openapi, Actuator, Flyway, Spring Data JPA, AWS SDK v2 (S3, SQS), jjwt or Spring Security OAuth2 resource server (HS256)
- [ ] Global JSON config: camelCase, ISO-8601 UTC dates (`WRITE_DATES_AS_TIMESTAMPS=false`), ignore unknown properties
- [ ] Global `@RestControllerAdvice` producing the shared error body (`code, message, details, traceId, timestamp`)
- [ ] `X-Request-Id` filter: read or generate, put in MDC, return in response, forward downstream
- [ ] JSON logging (logstash-logback-encoder) with `traceId`, `userId`
- [ ] Flyway pointed at `../contracts/database/migrations` (local) / `/contracts/database/migrations` (Docker); app boots against `make up`
- [ ] Dockerfiles (multi-stage, `eclipse-temurin:21-jre`, non-root user, `HEALTHCHECK` on `/actuator/health`) — coordinate with Manali

## Milestone 2 — Ingestion (Oct 12 – Oct 25)
- [ ] `POST /api/v1/documents` multipart upload: validate type (PDF/DOCX by **magic bytes**, not just extension) and size ≤ 25 MB → 415/413
- [ ] SHA-256 checksum; store in S3 at `documents/{dept}/{documentId}/v{n}/{filename}` (SSE-KMS in AWS, plain in LocalStack)
- [ ] Insert `documents` + `document_versions` (status `QUEUED`) in one transaction, then publish `DOCUMENT_INGEST_REQUESTED` (validate against JSON schema in a unit test)
- [ ] SQS consumer for `ekm-document-status`: idempotent by `eventId`; update version + document status, `page_count`, `chunk_count`, `error_*`, `indexed_at`; on INDEXED of version n set `documents.current_version = n`
- [ ] `GET /api/v1/documents` (paging/filter, **only allowed departments**), `GET /api/v1/documents/{id}`, `PATCH`, `DELETE` (soft delete + `UPDATE document_chunks SET is_deleted = true`)
- [ ] `POST /api/v1/documents/{id}/versions` (new version, `previousVersionId` in the event)
- [ ] `GET /api/v1/documents/{id}/download-url` (presigned GET, 5 min; append `#page=N` when `page` given)
- [ ] `GET /api/v1/departments`
- [ ] Integration test with Testcontainers: upload → message on queue → fake status message → document INDEXED

## Milestone 3 — Search & answer plumbing (Oct 26 – Nov 8)
- [ ] `AiServiceClient` (WebClient) with `X-Internal-Token`, `X-Request-Id`, timeouts (search 10 s, answer 60 s), retry once on 502/503
- [ ] `POST /api/v1/search` → ai-service `/internal/v1/search` (intersect requested departments with allowed)
- [ ] `POST /api/v1/chat` (non-streaming): create/continue conversation, generate `queryId`, send last 6 messages as history, persist USER + ASSISTANT `chat_messages` with citations JSON
- [ ] `GET /api/v1/documents/{id}/chunks/{chunkId}` for the citation preview (department check!)
- [ ] `GET/DELETE /api/v1/conversations…`

## Milestone 4 — Security & streaming (Nov 9 – Nov 20)
- [ ] `POST /api/v1/auth/login` (BCrypt, inactive users → 401, generic error message, `LOGIN_FAILED` audit event)
- [ ] JWT issue/validate exactly as `contracts/auth.md`; `GET /api/v1/users/me` with `allowedDepartments` + `permissions`
- [ ] Method security: MANAGER writes only own department; ADMIN all; `/api/v1/admin/**` ADMIN only
- [ ] Department isolation everywhere: 404 for other departments' documents/chunks + `ACCESS_DENIED` audit event
- [ ] **Leakage test suite**: run every `RBAC_DENIED` case in `resources/eval/golden_qa.jsonl` and `resources/eval/rbac_matrix.csv` as automated tests
- [ ] `POST /api/v1/chat/stream` SSE relay (`meta` → ai-service events → persist answer at `done`), keep-alive every 15 s, handle client disconnect
- [ ] CORS from `CORS_ALLOWED_ORIGINS`; rate-limit chat (e.g. 20 req/min/user, Bucket4j) → 429
- [ ] Admin users API (`/api/v1/admin/users`)

## Milestone 5 — Audit & analytics (Nov 21 – Nov 28)
- [ ] Publish `AUDIT_EVENT` for every action in the enum (async, never blocks the request)
- [ ] `audit-service`: consume `ekm-audit-events` idempotently (`event_id` unique) → `audit_logs`; `QUERY_ASKED` → `query_logs`; unanswered → upsert `knowledge_gaps` (`normalized_query`, `department_code`, `occurrences++`)
- [ ] Analytics endpoints: summary, queries-over-time, top-queries, top-documents, knowledge-gaps (+ PATCH), audit-logs
- [ ] Feedback endpoint `POST /api/v1/chat/messages/{id}/feedback`

## Milestone 6 — Integration & demo (Nov 29 – Dec 2)
- [ ] End-to-end run with the real ai-service + web on staging; run `make ingest-corpus` + golden QA
- [ ] Security checklist for the report: OWASP top-10 notes, no secrets in logs, JWT expiry, CORS, input validation
- [ ] Postman/Bruno collection or `.http` file in this folder for the demo

## Definition of done (every endpoint)
Matches the OpenAPI spec (verify with springdoc output or Schemathesis), validated input, shared error body, department check,
audit event, unit test + at least one integration test.

## Blocked / waiting on
- _none yet_
