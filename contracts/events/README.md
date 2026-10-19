# SQS message contracts

| Queue | Producer | Consumer | Schema | Example |
|---|---|---|---|---|
| `ekm-document-ingest` | core-api (Ayushi) | ai-service worker (Vedanti) | [document-ingest-requested.schema.json](document-ingest-requested.schema.json) | [example](examples/document-ingest-requested.json) |
| `ekm-document-status` | ai-service worker (Vedanti) | core-api (Ayushi) | [document-ingest-status.schema.json](document-ingest-status.schema.json) | [examples](examples/) |
| `ekm-audit-events` | core-api (Ayushi) | audit-service (Ayushi) | [audit-event.schema.json](audit-event.schema.json) | [example](examples/audit-event-query-asked.json) |

Rules for every queue:
- Message body = one JSON object (camelCase), validated against its schema by the producer in tests.
- Every message has `eventId` (UUID, for idempotency), `eventType`, `occurredAt`, `traceId` (the `X-Request-Id`).
- Consumers must be **idempotent**: the same `eventId` may arrive more than once (SQS at-least-once).
- Consumers delete the message only after successful processing; after 3 failed receives it goes to the `-dlq`.
- Consumers ignore unknown fields. Producers only add **optional** fields without a contract version bump.
- Status flow of a document version: `QUEUED` (core-api, on publish) → `PROCESSING` → `INDEXED` | `FAILED` (ai-service, via status queue).

Send a test message locally:
```bash
awslocal sqs send-message --queue-url http://localhost:4566/000000000000/ekm-document-ingest \
  --message-body file://contracts/events/examples/document-ingest-requested.json
```
