# contracts/ — the source of truth for every interface

If two services talk to each other, the shape of that conversation is defined here **before** code is written.
Every agent must code against these files and must never invent fields, endpoints, queues, tables or env vars.

| File | What it defines | Owner (approves changes) | Consumers |
|---|---|---|---|
| [conventions.md](conventions.md) | JSON casing, IDs, timestamps, enums, error body, headers, pagination | Manali | everyone |
| [ports-and-services.md](ports-and-services.md) | Service names, ports, hostnames, queue/bucket names | Manali | everyone |
| [auth.md](auth.md) | JWT claims, roles, department access rules, internal service token | Ayushi | everyone |
| [streaming.md](streaming.md) | Server-Sent Events format for streaming chat answers | Vedanti + Ayushi | Changhyun |
| [openapi/core-api.yaml](openapi/core-api.yaml) | Public REST API (browser → core-api) | Ayushi | Changhyun |
| [openapi/ai-service.yaml](openapi/ai-service.yaml) | Internal REST API (core-api → ai-service) | Vedanti | Ayushi |
| [events/](events/) | SQS message JSON Schemas + valid examples | Ayushi (ingest, audit), Vedanti (status) | Ayushi, Vedanti |
| [database/](database/) | PostgreSQL + pgvector schema (Flyway migrations), dev seed | Ayushi | Ayushi, Vedanti |
| [CHANGELOG.md](CHANGELOG.md) | Every contract change, dated | whoever changes a contract | everyone |

## How to change a contract

1. Open a question in `questions/to-<owner>/` (or `to-all/` if several services are affected) describing the change.
2. Once agreed, make a PR titled `[contracts] <change>` that touches **only** `contracts/` (+ `.env.example` if needed).
3. Add an entry to `CHANGELOG.md`. Prefer **additive** changes (new optional field) over breaking ones.
4. Every consumer listed above approves the PR. Then each owner updates their code.

## Validate locally

```bash
make lint-contracts    # redocly lint for OpenAPI + ajv validation of event examples
make mock-core-api     # Prism mock server of core-api on http://localhost:4010
make mock-ai-service   # Prism mock server of ai-service on http://localhost:4011
```
