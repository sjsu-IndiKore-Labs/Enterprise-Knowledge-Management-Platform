---
from: manali
to: ayushi
status: answered
priority: normal
created: 2026-11-06
related: contracts/ports-and-services.md, docker-compose.yml, ayushi_backend/core-api/Dockerfile, ayushi_backend/audit-service/Dockerfile
---

# Align backend runtime settings with the DevOps contract

## Context
The backend has now migrated to Maven and the DevOps CI/Jenkins jobs build it with Java 21. During integration review, the new backend settings do not yet match the existing runtime contract used by Compose and AWS.

## Question
Please update the backend implementation (or confirm an alternative) for the following:

1. `core-api` currently uses `server.port=8082`, while the contract and `docker-compose.yml` require port `8080`.
2. `core-api` defaults to `127.0.0.1:5432/knowledge_hub`; inside Compose it must reach the `postgres` service and the `ekm` database using the existing contract environment variables.
3. Both runtime Docker images should run as a non-root user and expose a healthcheck for `/actuator/health`, as required by the backend TODO and service contract.
4. `contracts/database/migrations/V1__init_schema.sql` and the copy under `core-api/src/main/resources/db/migration/` differ. Please confirm which is authoritative and make Flyway use one consistent migration.

## Proposed answer
Please align the backend to the existing contracts and Compose values: core-api on `8080`, PostgreSQL at `postgres:5432/ekm`, non-root images with `/actuator/health` healthchecks, and the contract migration as the single source of truth. I will then validate `make up-apps` and both actuator endpoints.

## Answer
**Ayushi (2026-11-24):** All 4 items are now aligned with the DevOps runtime contract:
1. `core-api` uses `server.port=${CORE_API_PORT:8080}` (and `audit-service` uses `${AUDIT_SERVICE_PORT:8081}`).
2. Both services default to `jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:ekm}` with user `ekm` and password `ekm_local_password`.
3. Both `core-api/Dockerfile` and `audit-service/Dockerfile` run as non-root user `ekm` and include a `HEALTHCHECK` on `/actuator/health`.
4. Flyway migrations (`V1`, `V2`, `V3`) are aligned with `contracts/database/migrations/V1__init_schema.sql` and seed all 12 dev users and 6 contract departments.

## Acknowledgements
