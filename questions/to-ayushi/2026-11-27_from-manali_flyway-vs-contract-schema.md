---
from: manali
to: ayushi
status: open
priority: normal
created: 2026-11-27
related: ayushi_backend/core-api/src/main/resources/db/migration/, contracts/database/migrations/V1__init_schema.sql, ayushi_backend/audit-service/src/main/java/com/helios/ekm/audit/entity/AuditLogRecord.java
---

# core-api's Flyway schema has drifted from `contracts/database/migrations`. Which one is the source of truth?

## Context
I tested the real core-api and audit-service images from `manali_devops/docker/java-service.Dockerfile`:
- **Fresh, empty Postgres (what staging RDS will be):** both start and are healthy. 
- **Database created from the contract schema (`make migrate-raw`, which everyone has done locally):**
  - core-api: Flyway `V2__contract_schema_m1_m3` fails and rolls back, so the app doesn't start.
  - audit-service: Hibernate validation fails with `audit_logs.metadata` found `jsonb`, expected `text`.
    The contract has `metadata JSONB`. Your `V3__m4_m5_auth_audit_analytics.sql` and `AuditLogRecord` use `TEXT`.

core-api runs Flyway from `classpath:db/migration`, not `/contracts/database/migrations` as agreed in
`2026-10-19_from-manali_container-and-db-conventions.md`. The image still contains the contract migrations, but they're unused.

## Question
1. Is `ayushi_backend/core-api/src/main/resources/db/migration` now the source of truth for the schema? If so,
   `contracts/database/migrations` + `contracts/CHANGELOG.md` should be updated to match (Vedanti's ai-service reads
   `document_chunks` directly).
2. `audit_logs.metadata`: keep the contract's `JSONB` (map it with `@JdbcTypeCode(SqlTypes.JSON)`) or change the contract to `TEXT`?

## Proposed answer
1. Yes. Your Flyway scripts become the source of truth, and you mirror them into `contracts/database/migrations` in a
   `[contracts]` PR. I'll retire `make migrate-raw` and tell everyone to reset their local DB with
   `docker compose down -v && make up` and let core-api migrate it.
2. Keep `JSONB` as in the contract.

## Answer
<!-- Filled by the recipient: name, date, decision, what changes (contract? TODO?) -->
