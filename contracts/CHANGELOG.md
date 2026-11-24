# Contracts changelog

Newest first. Every change to anything in `contracts/` or `.env.example` gets an entry.
Format: `YYYY-MM-DD — <author> — <what changed> — <who must update code>`

- 2026-10-19 — Manali — DevOps v1: new env var `POSTGRES_SSLMODE`; `ai-worker` is a separate container (same image as ai-service); ALB routes `/api/v1/*` to core-api; services read `PORT`; Dockerfile templates in `manali_devops/docker` — Ayushi (sslmode, Flyway path), Vedanti (worker entrypoint), Changhyun (standalone output, empty API base URL)
- 2026-10-19 — Manali — Initial contracts v1: core-api & ai-service OpenAPI, 3 SQS event schemas, DB schema V1, JWT claims, SSE format, conventions — everyone
