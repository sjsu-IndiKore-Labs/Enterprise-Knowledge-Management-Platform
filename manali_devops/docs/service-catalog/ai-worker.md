# Service card: ai-worker

**Service name:** `ai-worker`  
**Aliases:** ingestion worker, document worker, RAG ingestion pipeline  
**Owner:** Vedanti  
**Technology:** Python 3.11; same image as `ai-service`  
**Network port:** none; long-running worker process  
**Command:** `python -m app.ingestion.worker`

## Responsibility

The AI worker consumes document-ingest messages, reads the uploaded source file, extracts text, chunks the content, creates embeddings, writes indexed chunks to PostgreSQL/pgvector, and reports processing status back to `core-api` through the document-status queue.

## Processing flow

```text
document-ingest SQS message
        ↓
read object from S3 using document key
        ↓
extract PDF/DOCX text
        ↓
chunk text with configured overlap
        ↓
create embeddings
        ↓
write document chunks and vectors to PostgreSQL/pgvector
        ↓
send PROCESSING / INDEXED / FAILED status
```

## AWS permissions

The ECS task role grants:

- `s3:GetObject` on `documents/*`
- `kms:Decrypt` on the documents KMS key
- SQS receive, delete, visibility-change, send, and queue metadata permissions for document-ingest and document-status

The worker does not need S3 write or delete permission. It should use the default ECS credential provider chain rather than static AWS access keys.

## Reliability behavior

- Use long polling for the ingest queue.
- Keep the message when processing fails so SQS can retry it.
- Delete the message only after successful indexing and status publication.
- Allow the queue redrive policy to move repeatedly failing messages to the DLQ.
- Make processing idempotent by document version so retries do not create duplicate chunks.

## Troubleshooting

- No documents processed: inspect the ingest queue, worker task health, and worker logs.
- `AccessDenied` from S3: verify the key uses `documents/`, the task role is attached, and KMS decrypt is allowed.
- `FAILED` status: inspect PDF/DOCX extraction, embedding provider, database connectivity, and the worker DLQ.
- Duplicate or stale results: verify version-aware chunk replacement and the `is_current` retrieval filter.

## Source references

- `../../../contracts/events/document-ingest-requested.schema.json`
- `../../../contracts/events/document-ingest-status.schema.json`
- `../../../contracts/ports-and-services.md`
- `../../../../vedanti_ai/README.md`
- `../../../../vedanti_ai/TODO.md`
- `../../infra/terraform/ecs.tf`
- `../../infra/terraform/iam.tf`
