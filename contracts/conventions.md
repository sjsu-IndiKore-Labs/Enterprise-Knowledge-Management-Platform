# Cross-service conventions

These rules apply to **every** service regardless of language or which coding agent wrote it.
Most integration bugs between independently-built services come from disagreeing on exactly these things.

## JSON
- Field names: **camelCase** in all REST bodies and SQS messages (`documentId`, `pageNumber`, `createdAt`).
  - Java: Jackson default. Python: Pydantic models with `alias_generator=to_camel, populate_by_name=True`
    and `response_model_by_alias=True`. TypeScript: types generated from OpenAPI (`openapi-typescript`).
- Database columns: **snake_case**. Never leak DB column names into JSON.
- Omit unknown/absent optional fields **or** send `null` — consumers must accept both. Consumers must **ignore unknown fields** (forward-compatibility).

## Values
| Kind | Format | Example |
|---|---|---|
| IDs | UUID v4 string, lowercase | `"7d1f0a3e-2b5c-4c1e-9f8a-0b6e2d3c4a5b"` |
| Timestamps | ISO-8601, UTC, `Z` suffix, millisecond precision optional | `"2026-10-19T17:05:00Z"` |
| Dates | ISO-8601 date | `"2026-12-02"` |
| Enums | `UPPER_SNAKE_CASE` strings | `"INDEXED"`, `"ADMIN"` |
| Department codes | one of `GENERAL, HR, ENGINEERING, FINANCE, LEGAL, MARKETING` | `"HR"` |
| Page numbers | 1-based integers; `null` when the format has no pages (DOCX) | `3` |
| Scores | float 0.0–1.0, higher = more relevant | `0.82` |
| Money / sizes | integers in smallest unit (`sizeBytes`) | `248133` |

## Error body (all REST services, all 4xx/5xx)
```json
{
  "code": "DOCUMENT_NOT_FOUND",
  "message": "Document 7d1f... does not exist or you do not have access.",
  "details": { "documentId": "7d1f..." },
  "traceId": "c0a8012e-...",
  "timestamp": "2026-10-19T17:05:00Z"
}
```
Standard codes: `VALIDATION_ERROR` (400), `UNAUTHENTICATED` (401), `FORBIDDEN` (403), `NOT_FOUND`/`DOCUMENT_NOT_FOUND` (404),
`CONFLICT` (409), `PAYLOAD_TOO_LARGE` (413), `UNSUPPORTED_MEDIA_TYPE` (415), `RATE_LIMITED` (429),
`INTERNAL_ERROR` (500), `UPSTREAM_UNAVAILABLE` (502/503 — e.g. ai-service or OpenAI down).
For RBAC, return **404 instead of 403** when revealing existence would leak information (documents of other departments).

## HTTP headers
| Header | Who sends | Purpose |
|---|---|---|
| `Authorization: Bearer <jwt>` | browser → core-api | user identity (see auth.md) |
| `X-Request-Id: <uuid>` | anyone; generate if missing | propagated to every downstream call, SQS message (`traceId`) and log line |
| `X-Internal-Token: <token>` | core-api → ai-service | service-to-service auth (env `INTERNAL_SERVICE_TOKEN`) |

## Pagination (list endpoints)
Query: `?page=0&size=20&sort=createdAt,desc` (0-based, Spring style). Response:
```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 134, "totalPages": 7 }
```

## API versioning
Public paths start with `/api/v1`, internal ai-service paths with `/internal/v1`. Breaking change → new version path.

## Logging (all services)
One JSON object per line to stdout (CloudWatch picks it up): `timestamp, level, service, traceId, userId?, message, ...`.
Never log: JWTs, passwords, `OPENAI_API_KEY`, full document contents. Query text **may** be logged (it is audited anyway).

## Health
Every service exposes `GET /health` (ai-service, web) or `GET /actuator/health` (Spring) returning 200 with `{"status":"UP"}`.
Docker and ECS health checks use these paths.
