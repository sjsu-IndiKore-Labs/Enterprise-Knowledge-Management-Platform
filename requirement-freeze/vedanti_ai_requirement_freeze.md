# AI and RAG Requirement Freeze

**Enterprise Knowledge Management Platform**

| | |
|---|---|
| Project | Enterprise Knowledge Management Platform |
| Scope owner | Vedanti — AI and RAG Pipeline |
| Freeze date | November 4, 2026 |
| Target demonstration | December 2, 2026 |

This document defines the AI and Retrieval-Augmented Generation (RAG) MVP scope for the December 2, 2026 demonstration.

## Objective

Provide a secure internal AI service that processes approved PDF and DOCX documents, indexes their content, retrieves only authorized information, and generates grounded answers with source citations.

## In Scope

- Python 3.11 FastAPI AI service on port 8000.
- Health endpoint and contract-aligned internal APIs for search, non-streaming answers, and SSE answer streaming.
- PDF extraction with page numbers and DOCX extraction with section titles and table content.
- Text cleaning and token-aware chunking using the agreed default of 500 tokens with 80-token overlap.
- Document metadata carried into every chunk, including department, category, title, and version.
- OpenAI `text-embedding-3-small` embeddings with 1536 dimensions.
- Deterministic fake embedding and answer providers for local development and testing without an OpenAI key.
- SQS ingestion worker that reads document-ingestion events, downloads documents from S3, processes them, and publishes processing status events.
- Writing document chunks and embeddings to PostgreSQL with pgvector.
- Idempotent processing by document version and current-version handling.
- Hybrid retrieval combining PostgreSQL keyword search and pgvector semantic search.
- Mandatory department filtering in SQL using the `allowedDepartments` supplied by core-api.
- Grounded answer generation using `gpt-4o-mini` and retrieved context only.
- Source citations with document, version, page or section, chunk, and score information.
- Similarity threshold handling that returns the agreed not-found response without calling the LLM when context is insufficient.
- Contract-compatible SSE streaming with token, citations, done, and error events; the non-streaming answer endpoint remains the required fallback.
- Basic protection against prompt injection from user questions and document content.
- Unit tests for extraction, chunking, embeddings, retrieval, guardrails, and ingestion status handling.
- Evaluation using the provided corpus, golden QA set, and RBAC matrix.
- Docker support, environment-based configuration, and a non-root container with a health check.

## Deferred or Out of Scope

- File formats beyond PDF and DOCX.
- OCR for scanned documents; scanned files will return `NO_EXTRACTABLE_TEXT`.
- Production-grade multi-region infrastructure, advanced autoscaling, or distributed tracing.
- Advanced knowledge-gap clustering; basic unanswered-query signals are sufficient for the MVP.
- Features outside the agreed SSE streaming contract. Streaming is part of the integration path, but the non-streaming answer endpoint remains the fallback if streaming has an integration failure during the demonstration.
- Independent AI model training or fine-tuning.
- Post-retrieval permission filtering; permissions must be enforced before ranking in SQL.

## Acceptance Criteria

- [ ] The AI service starts locally using documented commands and exposes `GET /health`.
- [ ] The service works with `AI_PROVIDER=fake` without an OpenAI key.
- [ ] A PDF and DOCX can be extracted, chunked, and represented with correct page or section metadata.
- [ ] A document-ingestion event produces `PROCESSING` followed by exactly one `INDEXED` or `FAILED` status event.
- [ ] Chunks are written to `document_chunks` with 1536-dimensional embeddings and required metadata.
- [ ] Search returns normalized semantic, keyword, and combined scores.
- [ ] Retrieval always applies the allowed-department filter in SQL before ranking.
- [ ] Answer responses include `answered`, `confidence`, citations, latency, and usage fields required by the contract.
- [ ] Unsupported, corrupt, empty, and unreadable documents return the correct failure code.
- [ ] Unauthorized documents never appear in retrieved context, citations, or generated answers.
- [ ] The golden evaluation set can be executed and reports retrieval, citation, answer, refusal, leakage, and latency results.
- [ ] The service includes a Dockerfile and can run as part of the team’s Docker Compose environment.

## Dependencies From Other Owners

| Dependency | Owner | Purpose |
|---|---|---|
| Database migrations and document/version records | Ayushi | Read document metadata and write AI-owned `document_chunks` rows |
| `DOCUMENT_INGEST_REQUESTED` and status event contracts | Ayushi | Receive ingestion work and report processing results |
| PostgreSQL with pgvector and LocalStack S3/SQS | Manali | Local execution and integration testing |
| Docker and service health-check conventions | Manali | Container packaging and deployment |
| Core-api search, chat, and authorization integration | Ayushi | Send `allowedDepartments` and consume AI responses |
| Frontend citation and answer display | Changhyun | Present AI answers and citations to users |

## Freeze Rule

New AI capabilities, model providers, document formats, or retrieval strategies require team agreement and must not delay the December 2 demonstration. Any proposed addition must identify its value, owner, time cost, and impact on the committed MVP.

## Decision

This scope is the baseline for AI/RAG implementation and integration. The AI service will prioritize secure, contract-compatible document ingestion, retrieval, and grounded answers over optional advanced features.
