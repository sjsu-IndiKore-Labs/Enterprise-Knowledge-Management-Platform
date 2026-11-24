# Service card: core-api

**Service name:** `core-api`  
**Aliases:** core API, knowledge API, public API  
**Owner:** Ayushi  
**Technology:** Spring Boot 3, Java 21, Maven  
**Port:** `8080`  
**Health check:** `GET /actuator/health`

## Responsibility

`core-api` is the public backend boundary for the portal. It handles authentication, users and effective permissions, document metadata, upload coordination, document versions, search/chat proxying, and admin analytics endpoints defined by the public OpenAPI contract.

## Storage and messaging

- Writes uploaded source files to private S3 under the `documents/*` prefix.
- Creates document and version metadata in PostgreSQL.
- Publishes document-ingest requests to the document-ingest SQS queue.
- Consumes document-status messages to update processing state.
- Publishes audit events to the audit-events SQS queue.
- Calls `ai-service` through ECS Service Connect at `http://ai-service:8000`.

## Security responsibilities

- Validates the caller's JWT.
- Determines department access from JWT claims and server-side authorization.
- Must not trust a department supplied by the browser.
- Has S3 object read/write/delete permissions for `documents/*` and KMS encrypt/decrypt permissions.
- Owns the public authorization boundary; downstream services must not be exposed directly to the browser.

## API examples

- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `GET /api/v1/documents`
- `POST /api/v1/documents`
- `GET /api/v1/documents/{documentId}`
- Search, chat streaming, and admin analytics endpoints defined in `contracts/openapi/core-api.yaml`.

## Deployment

- ECS service name: `ekm-staging-core-api`.
- Container port: `8080`.
- PostgreSQL uses TLS in AWS staging through `POSTGRES_SSLMODE=require`.
- Flyway migrations run as part of backend startup according to the backend implementation.

## Troubleshooting

- `401` or `403`: inspect JWT validity, role, and department authorization.
- Upload remains `QUEUED`: inspect the document-ingest queue and `ai-worker` logs.
- Upload becomes `FAILED`: inspect document-status messages and worker extraction logs.
- Chat or search returns a downstream error: inspect the `ai-service` health endpoint and Service Connect configuration.

## Source references

- `../../../contracts/openapi/core-api.yaml`
- `../../../contracts/auth.md`
- `../../../contracts/ports-and-services.md`
- `../../../../ayushi_backend/README.md`
- `../../infra/terraform/ecs.tf`
- `../../infra/terraform/iam.tf`
