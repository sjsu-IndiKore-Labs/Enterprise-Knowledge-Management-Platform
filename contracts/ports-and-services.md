# Services, ports and resource names

| Service | Owner | Tech | Local port | docker-compose hostname | Health |
|---|---|---|---|---|---|
| `web` | Changhyun | Next.js 14+ / TypeScript | 3000 | `web` | `GET /api/health` |
| `core-api` | Ayushi | Spring Boot 3, Java 21 | 8080 | `core-api` | `GET /actuator/health` |
| `audit-service` | Ayushi | Spring Boot 3, Java 21 | 8081 | `audit-service` | `GET /actuator/health` |
| `ai-service` | Vedanti | Python 3.11, FastAPI | 8000 | `ai-service` | `GET /health` |
| `postgres` | Manali (infra) / Ayushi (schema) | PostgreSQL 16 + pgvector | 5432 | `postgres` | `pg_isready` |
| `localstack` | Manali | LocalStack (S3, SQS) | 4566 | `localstack` | `GET /_localstack/health` |
| Prism mock of core-api | — | `@stoplight/prism-cli` | 4010 | — | — |
| Prism mock of ai-service | — | `@stoplight/prism-cli` | 4011 | — | — |

Who calls whom:
```
browser ──HTTP/SSE──> core-api ──HTTP/SSE (X-Internal-Token)──> ai-service
core-api ──SQS ekm-document-ingest──> ai-service (worker)
ai-service ──SQS ekm-document-status──> core-api
core-api ──SQS ekm-audit-events──> audit-service
core-api, audit-service, ai-service ──JDBC/psycopg──> postgres
core-api (upload, presigned download) & ai-service (download for parsing) ──> S3
```
The browser only ever talks to `core-api`. ai-service is never exposed publicly.

## AWS resource names
| Resource | Local (LocalStack) | AWS (`<env>` = dev/staging/prod) |
|---|---|---|
| S3 bucket | `ekm-documents-local` | `ekm-documents-<env>-<accountId>` (SSE-KMS) |
| SQS ingest queue | `ekm-document-ingest` (+ `-dlq`) | `ekm-<env>-document-ingest` (+ `-dlq`) |
| SQS status queue | `ekm-document-status` (+ `-dlq`) | `ekm-<env>-document-status` (+ `-dlq`) |
| SQS audit queue | `ekm-audit-events` (+ `-dlq`) | `ekm-<env>-audit-events` (+ `-dlq`) |
| DLQ policy | maxReceiveCount = 3 | same |
| Visibility timeout (ingest) | 300 s | 300 s |

## S3 object key layout
```
documents/{departmentCode}/{documentId}/v{version}/{sanitizedOriginalFilename}
e.g. documents/HR/7d1f0a3e-.../v2/pto_policy.pdf
```
Only core-api writes objects. ai-service has read-only access. Browser downloads via presigned GET URLs (5 min).
