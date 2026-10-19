# Integration guide — how the pieces fit (read before integrating)

## Flow 1 — Upload & ingestion
```mermaid
sequenceDiagram
  participant B as web (Changhyun)
  participant C as core-api (Ayushi)
  participant S as S3
  participant Q as SQS
  participant A as ai-service worker (Vedanti)
  participant P as Postgres
  B->>C: POST /api/v1/documents (multipart, JWT)
  C->>C: check role/department, type, size
  C->>S: PutObject documents/{dept}/{id}/v1/{file}
  C->>P: INSERT documents, document_versions (QUEUED)
  C->>Q: DOCUMENT_INGEST_REQUESTED (ekm-document-ingest)
  C-->>B: 202 Document{status: QUEUED}
  A->>Q: receive
  A->>Q: DOCUMENT_INGEST_STATUS PROCESSING
  A->>S: GetObject
  A->>A: extract → chunk → embed
  A->>P: INSERT document_chunks (+ is_current=false on previous version)
  A->>Q: DOCUMENT_INGEST_STATUS INDEXED (ekm-document-status)
  C->>Q: receive status → UPDATE document_versions/documents
  B->>C: GET /api/v1/documents/{id} (poll every 3 s) → INDEXED
```

## Flow 2 — Ask a question (streaming)
```mermaid
sequenceDiagram
  participant B as web
  participant C as core-api
  participant A as ai-service
  participant P as Postgres
  participant O as OpenAI
  B->>C: POST /api/v1/chat/stream {message, conversationId?}
  C->>C: validate JWT → allowedDepartments = {dept, GENERAL} (ADMIN: all)
  C-->>B: event: meta {queryId, conversationId, messageId}
  C->>A: POST /internal/v1/answer/stream {queryId, query, allowedDepartments, history}
  A->>O: embed query
  A->>P: hybrid search WHERE department_code = ANY(allowed)
  alt best score < RAG_MIN_SIMILARITY
    A-->>C: token "I couldn't find this…" , citations [], done {answered:false}
  else
    A->>O: chat completion (stream)
    A-->>C: token… citations done {answered:true}
  end
  C-->>B: relays token / citations / done
  C->>P: INSERT chat_messages (USER, ASSISTANT + citations)
  C->>C: publish AUDIT_EVENT QUERY_ASKED → audit-service → query_logs (+ knowledge_gaps if unanswered)
```

## Integration checkpoints (put them in the weekly sync)
| Date | Checkpoint | Who |
|---|---|---|
| Oct 21 | Contracts v1 acknowledged by all four; `make up` works on every laptop | all |
| Oct 25 | Upload via UI → S3 → SQS message visible; ai-service consumes the example message and writes chunks (fake embeddings OK) | Changhyun, Ayushi, Vedanti |
| Nov 1 | Status loop closed: document shows INDEXED in UI | Ayushi, Vedanti, Changhyun |
| Nov 8 | `/api/v1/chat` non-streaming answers golden Q001–Q010 with citations | Ayushi, Vedanti |
| Nov 15 | Login + RBAC; zero leakage on RBAC_DENIED golden cases; streaming in UI | all |
| Nov 20 | Staging on ECS reachable; CI/CD green | Manali |
| Nov 28 | Analytics dashboard with real data; eval numbers | Ayushi, Changhyun, Vedanti |
| Dec 1 | Full rehearsal on staging | all |

## Debugging across services
- Every request/message carries `X-Request-Id` / `traceId`. Grep it in all logs: `make logs | grep <traceId>`.
- Check queues: `aws --endpoint-url http://localhost:4566 sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/ekm-document-ingest-dlq --attribute-names ApproximateNumberOfMessages`
- Something doesn't match the contract? Don't patch around it silently — open a question to the owner.
