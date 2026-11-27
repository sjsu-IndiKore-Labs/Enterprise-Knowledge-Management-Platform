---
from: manali
to: ayushi
status: open
priority: blocking
created: 2026-11-27
related: ayushi_backend/core-api/src/main/resources/db/migration/, contracts/database/migrations/V1__init_schema.sql, ayushi_backend/core-api/src/main/java/backend/knowledge_api_service/service/DocumentStatusSqsConsumer.java, ayushi_backend/core-api/src/main/java/backend/knowledge_api_service/service/AiServiceClient.java
---

# Full-stack run on a fresh DB (= staging RDS): 5 core-api issues block ingestion and chat

## Context
On 2026-11-27 I ran the whole stack (`docker compose --profile apps`, `AI_PROVIDER=fake`) on **empty volumes**, the
same as staging RDS, then ran `make ingest-corpus`, search, chat, chat/stream and k6. Everything below reproduces
locally. 1 and 2 stop every document from ever becoming `INDEXED`, so the demo can't work without them.

### 1. `document_chunks` has no `embedding` / `content_tsv` (blocking)
Your Flyway `V2__contract_schema_m1_m3.sql` creates `document_chunks` without the contract's
`embedding vector(1536) NOT NULL`, `content_tsv tsvector GENERATED ...`, `idx_chunks_embedding_hnsw` and `idx_chunks_tsv`
(`contracts/database/migrations/V1__init_schema.sql` lines 92–113). The ai-worker insert fails for every document:
`column "embedding" of relation "document_chunks" does not exist` → version `FAILED / INTERNAL_ERROR`.

### 2. `INDEXED` status events are lost (blocking)
`DocumentStatusSqsConsumer.pollStatusQueue()` calls `handleStatusEvent()` on `this`, so its `@Transactional`
never applies (Spring proxy self-invocation). For `INDEXED`, the `@Modifying` chunk update throws
`TransactionRequiredException: Executing an update/delete query`. The eventId is already in `processedEventIds`, so the redelivery is
logged as "Ignoring duplicate" and deleted. Result: `documents.status` stays `PROCESSING` forever.
Fix: move `handleStatusEvent` into a separate `@Service` (or use `TransactionTemplate`), and add the eventId only after a successful commit.

### 3. Every DOCX upload returns 500
`documents.file_type VARCHAR(64)`, but the DOCX MIME type
`application/vnd.openxmlformats-officedocument.wordprocessingml.document` is 71 chars:
`value too long for type character varying(64)`. 4 of the 19 corpus files fail (HR-002, ENG-002, LEG-001, MKT-002).

### 4. core-api → ai-service requests offer HTTP/2 (h2c)
`AiServiceClient` builds `java.net.http.HttpClient` without `.version(...)`, so it defaults to HTTP/2 and sends an
`Upgrade: h2c` on plain HTTP. uvicorn's default parser dropped the body and core-api got 400 → `UPSTREAM_UNAVAILABLE` (502)
for search and chat. **I've worked around it in the Python image** (`uvicorn --http h11`), but please add
`.version(HttpClient.Version.HTTP_1_1)` so it doesn't depend on that.

### 5. Every ADMIN chat fails (502), because `OPERATIONS` isn't in the contract
`V2__contract_schema_m1_m3.sql` seeds an `OPERATIONS` department, `Department.java` has `OPERATIONS`, and ADMIN gets it
in `allowedDepartments`. `DepartmentCode` in both OpenAPI specs (and ai-service) is
`[GENERAL, HR, ENGINEERING, FINANCE, LEGAL, MARKETING]`, so ai-service rejects the request with 422 → core-api 502
`UPSTREAM_UNAVAILABLE`. Reproduced for `alice.admin`. Every other seed user is fine. In the k6 run this was 144 of 2976 requests.
Fix: drop `OPERATIONS` (or add it to the contract via a `[contracts]` PR and tell Vedanti).

### Also (not blocking for staging)
- `audit_logs.metadata` is `TEXT` in your V3/entity but `JSONB` in the contract, so audit-service fails on a contract-built DB.
- core-api runs Flyway from `classpath:db/migration`, not `/contracts/database/migrations`. Local DBs built with
  `make migrate-raw` fail your V2 migration.

## Question
1. Can you fix 1–3 and 5 (and 4) before the staging deploy? The demo is Dec 2.
2. Is `ayushi_backend/core-api/src/main/resources/db/migration` now the schema source of truth?

## Proposed answer
1. Add a `V4__` migration that brings `document_chunks` to the contract (columns + both indexes) and widens
   `documents.file_type` to `VARCHAR(128)`. Fix the self-invocation. Pin HTTP/1.1.
2. Yes. Your Flyway scripts are the source of truth, mirrored into `contracts/database/migrations` in a `[contracts]` PR.
   I'll retire `make migrate-raw` and tell everyone to `make reset && make up-apps`.

## Answer
<!-- Filled by the recipient: name, date, decision, what changes (contract? TODO?) -->
