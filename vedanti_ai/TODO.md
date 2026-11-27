# TODO — Vedanti (AI / RAG Pipeline)

> Agents: read [`/AGENTS.md`](../AGENTS.md) first. This file is Vedanti's plan; other agents read it for context.
> Tick `[x]` when done, add items you discover, and keep "Blocked / waiting on" current.

## Your role in one paragraph
You build `ai-service`, the **brain** of the platform. (1) **Ingestion worker**: consume `DOCUMENT_INGEST_REQUESTED` from SQS,
download the file from S3, extract text per page (PDF) or per section (DOCX) incl. tables, clean it, chunk it, embed chunks with
`text-embedding-3-small` (1536 dims) and write them to `document_chunks` in pgvector, then report `PROCESSING`/`INDEXED`/`FAILED`
on the status queue. (2) **Retrieval**: hybrid semantic (pgvector cosine) + keyword (Postgres full-text) search, always
pre-filtered by the `allowedDepartments` core-api sends. (3) **Answering**: grounded prompt to `gpt-4o-mini`, strict "answer only
from context", `[n]` citations, "not found" below a similarity threshold, streamed as SSE. (4) **Evaluation & knowledge gaps**:
measure faithfulness/relevance/recall on the golden set and decide `answered` / `confidence` for gap detection.

## Current implementation status — Nov 27, 2026

Verified locally with the fake provider: contract-shaped FastAPI routes and
errors, token authentication, request IDs, PDF/DOCX extraction and cleaning,
metadata-preserving chunking with DOCX source order, deterministic embeddings,
OpenAI adapters with batching/retry/token counting, in-memory hybrid retrieval,
department and version isolation, grounded answer generation, citation
filtering, injection refusal, SSE events, S3/SQS worker code, database-backed
reindex queuing, Docker configuration, twelve automated tests, the 40-question
evaluator, and the 3-strategy chunking benchmark. Contract lint and event-schema
validation also pass.

Local offline tests now include a corpus runner that uses the worker's ingestion
pipeline method and supports simulated department access. Full platform
integration is still pending: the current develop Compose file does not define
the worker service, and the latest backend upload path must be verified against
the current S3/SQS contracts. Staging AWS access and production-provider
evaluation also remain unverified.

## You PROVIDE
| To | What | Contract |
|---|---|---|
| Ayushi | `POST /internal/v1/search`, `/internal/v1/answer`, `/internal/v1/answer/stream`, `GET /health` | `contracts/openapi/ai-service.yaml`, `contracts/streaming.md` |
| Ayushi | `DOCUMENT_INGEST_STATUS` messages; rows in `document_chunks` | `contracts/events/document-ingest-status.schema.json`, `contracts/database/` |
| Everyone | `AI_PROVIDER=fake` mode so others can run the stack without an OpenAI key | `.env.example` |
| Manali | Dockerfile (`python:3.11-slim`, non-root), JSON logs, `/health` | `contracts/ports-and-services.md` |
| Report | RAG evaluation numbers + chunking benchmark | `resources/eval/README.md` |

## You CONSUME
| From | What | Until it's ready, use |
|---|---|---|
| Ayushi | `DOCUMENT_INGEST_REQUESTED` messages + `documents`/`document_versions` rows; DB schema | send `contracts/events/examples/document-ingest-requested.json` with `awslocal`; upload the file to LocalStack S3 yourself; insert rows by hand / via `make seed` |
| Manali | Postgres+pgvector, LocalStack, OpenAI key handling in AWS (Secrets Manager) | `make up` |
| Resources | Corpus, golden QA, RBAC matrix | `resources/` |

## Milestone 1 — catch-up (was due Oct 11) — finish by **Oct 21**
- [ ] Acknowledge `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`; review `ai-service.yaml`, event schemas, `document_chunks` table
- [x] FastAPI skeleton: `/health`, `X-Internal-Token` dependency, shared error body, `X-Request-Id` → logs, pydantic-settings with names from `.env.example`, camelCase aliases
- [x] **Baseline chunking & embedding benchmark** on `resources/corpus`: compare 3 strategies × top-k ∈ {4, 6, 8}; results are in `eval/benchmarks/latest.json` and defaults are `500/80/6`
- [x] `FakeEmbeddings` (deterministic hash → 1536-d unit vector) + `FakeLLM` for `AI_PROVIDER=fake`

## Milestone 2 — Ingestion pipeline (Oct 12 – Oct 25)
- [x] SQS worker: long polling (20 s), visibility timeout 300 s, idempotent per `versionId`, delete message only on success, and DLQ-compatible retries
- [x] Send `PROCESSING`, then one pipeline result `INDEXED` or `FAILED` with contract fields and error codes
- [x] PDF extraction per page with repeated header/footer removal
- [x] DOCX extraction with paragraphs, tables, source order, and current heading as `section_title`
- [x] Edge-case error mapping for scanned, corrupt/encrypted, unsupported, and empty files
- [x] Metadata: carry `department`, `category`, `title`, and `version` into every chunk row

## Milestone 3 — Embeddings, hybrid search, grounded answers (Oct 26 – Nov 8)
- [x] Batch embeddings (≤ 100 inputs/request), retries with backoff, and token counting with tiktoken
- [x] Write chunks with `embedding vector(1536)` and atomically retire previous-version chunks
- [x] Retriever: semantic + keyword with mandatory department/current/deleted SQL filters and weighted fusion
- [x] `/internal/v1/search` returns normalized score fields
- [x] Grounded prompt and citation-aware answer generation
- [x] Similarity threshold, citation filtering, and prompt-injection refusal
- [x] `/internal/v1/answer` with usage, latency, and confidence
- [x] Query embedding cache (in-memory LRU)

## Milestone 4 — Streaming & isolation (Nov 9 – Nov 20)
- [x] `/internal/v1/answer/stream` with `token*` → `citations` → `done` (no `meta`)
- [x] Use conversation history to rewrite follow-up questions before retrieval
- [x] Automated department/version isolation tests, including forbidden-department filtering

## Milestone 5 — Evaluation & knowledge gaps (Nov 21 – Nov 28)
- [x] `eval/run_eval.py`: run all 40 golden questions with department filtering and report retrieval recall, citation accuracy, answer/refusal accuracy, leakage, and latency
- [x] Tune chunking/top-k baseline; document results in `eval/benchmarks/`
- [x] Knowledge-gap signal: `answered=false` below threshold with calibrated retrieval confidence
- [ ] (Optional) `/internal/v1/knowledge-gaps/cluster`: embed unanswered queries, cluster (agglomerative / k-means), label clusters with the LLM

## Milestone 6 — Integration & demo (Nov 29 – Dec 2)
- [ ] Ingest the full corpus on staging (`make ingest-corpus`), rerun eval on staging, put final numbers in the slide deck — requires the team staging environment
- [ ] Cost estimate: tokens per query × expected volume (for the report)

## Definition of done
Matches `ai-service.yaml` + event schemas exactly (camelCase), department filter is in SQL (not post-filtering),
works with `AI_PROVIDER=fake`, unit tests for extractors/chunker/retriever, no document text or API key in logs.

## Blocked / waiting on
- See [integration handoff](INTEGRATION_HANDOFF.md) for November 26 verification results and remaining responsibilities.
- Full local platform testing requires an AI worker service in Compose and current Core API integration; Docker/Colima is installed, but the current compose does not expose that complete flow.
- Staging runtime read and authenticated E2E checks remain unverified.
- November 27: corrected POSTGRES environment naming/TLS, failed ingestion acknowledgement, generated-refusal/citation handling, and hyphen/space tokenization in offline retrieval. Twenty-one tests pass; actual database, queue and authenticated application integration remain pending.
