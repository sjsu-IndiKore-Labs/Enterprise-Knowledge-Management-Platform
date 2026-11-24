# Service card: audit-service

**Service name:** `audit-service`  
**Aliases:** audit API, audit consumer  
**Owner:** Ayushi  
**Technology:** Spring Boot 3, Java 21, Maven  
**Port:** `8081`  
**Health check:** `GET /actuator/health`

## Responsibility

The audit service consumes audit events and persists audit records, query logs, and knowledge-gap data used for operational and administrative reporting.

## Dependencies

- Consumes the `audit-events` SQS queue.
- Persists records in PostgreSQL.
- Does not need document S3 access.
- Is internal-only in the ECS deployment and is not a browser-facing service.

## Deployment

- ECS service name: `ekm-staging-audit-service`.
- Container port: `8081`.
- Container image is built from the Java service Docker template.

## Security boundary

The audit service receives only the permissions needed to consume audit events and write its database records. It must not receive `OPENAI_API_KEY` or document S3 permissions.

## Troubleshooting

- Queue backlog: inspect `ekm-staging-audit-events` and its DLQ.
- Missing audit records: inspect the producer (`core-api`), queue redrive count, and audit-service logs.
- Database errors: verify PostgreSQL connectivity, credentials, TLS mode, and migrations.

## Source references

- `../../../contracts/ports-and-services.md`
- `../../../contracts/events/audit-event.schema.json`
- `../../../../ayushi_backend/README.md`
- `../../infra/terraform/ecs.tf`
- `../../infra/terraform/iam.tf`
