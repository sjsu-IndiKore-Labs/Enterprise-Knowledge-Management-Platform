---
from: manali
to: vedanti
status: open
priority: blocking
created: 2026-11-27
related: vedanti_ai/app/config.py, .env.example, manali_devops/infra/terraform/ecs.tf
---

# ai-service reads `POSTGRESQL_*` but every environment sets `POSTGRES_*`, so it can't reach RDS on staging

## Context
`vedanti_ai/app/config.py` declares `postgresql_host`, `postgresql_port`, `postgresql_db`, `postgresql_user`,
`postgresql_password`. pydantic-settings maps these to env vars `POSTGRESQL_HOST` … `POSTGRESQL_PASSWORD`.

The contract (`.env.example`), docker-compose and the ECS task definitions (`manali_devops/infra/terraform/ecs.tf`,
`secrets.tf`) only provide `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.

On staging (`APP_ENV=staging`) `create_store()` picks the Postgres store, but with the defaults
(`localhost`, password `ekm_local_password`). **Both ai-service and ai-worker will fail to connect to RDS.**
Locally it only works by accident: `postgresql_host` stays `localhost`, so you get the in-memory store.

The JDBC side (core-api, audit-service) already reads `POSTGRES_*`.

## Question
Can you switch the settings to the contract names?

## Proposed answer
Rename the fields to `postgres_host`, `postgres_port`, `postgres_db`, `postgres_user`, `postgres_password`
(or keep the names and add `validation_alias="POSTGRES_HOST"` etc.). While you're there:
- also read `POSTGRES_SSLMODE` and add `sslmode={settings.postgres_sslmode}` to the conninfo in `app/db.py`
  (`disable` locally, `require` in AWS). This is still open from `2026-10-19_from-manali_worker-entrypoint-and-container.md`.
- `create_store()` decides "real DB?" by `postgresql_host != "postgres"`. That breaks once the host is the RDS endpoint
  outside staging/prod; consider deciding on `APP_ENV` / an explicit flag instead.

I won't add `POSTGRESQL_*` vars to ECS. AGENTS.md says env var names come only from `.env.example`.

## Answer
<!-- Filled by the recipient: name, date, decision, what changes (contract? TODO?) -->
