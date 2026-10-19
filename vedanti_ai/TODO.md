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
- [ ] FastAPI skeleton: `/health`, `X-Internal-Token` dependency, shared error body, `X-Request-Id` → logs, pydantic-settings with names from `.env.example`, camelCase aliases
- [ ] **Baseline chunking & embedding benchmark** on `resources/corpus`: compare 3 strategies (fixed 500 tokens/80 overlap; heading/section-aware; page-aware recursive) × top-k ∈ {4, 6, 8} using Recall@k on `golden_qa.jsonl`. Save results in `eval/benchmarks/` and pick defaults (`CHUNK_SIZE_TOKENS`, `CHUNK_OVERLAP_TOKENS`, `RAG_TOP_K`)
- [ ] `FakeEmbeddings` (deterministic hash → 1536-d unit vector) + `FakeLLM` (returns first chunk sentence with `[1]`) for `AI_PROVIDER=fake`

## Milestone 2 — Ingestion pipeline (Oct 12 – Oct 25)
- [ ] SQS worker: long polling (20 s), visibility timeout 300 s, idempotent per `versionId` (delete existing chunks of that version before insert), delete message only on success, let SQS → DLQ after 3 failures
- [ ] Send `PROCESSING` at start, then exactly one `INDEXED` (pageCount, chunkCount, embeddingTokens, processingMs) or `FAILED` (`errorCode` from the schema enum)
- [ ] PDF extraction per page (pypdf / pdfplumber for tables) — strip the running header/footer (`Helios Systems Inc. — Internal — …`)
- [ ] DOCX extraction (python-docx) — paragraphs + tables, track the current heading as `section_title`, `page_number = NULL`
- [ ] Edge cases from `resources/edge-cases/`: scanned → `NO_EXTRACTABLE_TEXT`, corrupt/encrypted → `CORRUPT_FILE`, empty DOCX → `NO_EXTRACTABLE_TEXT`, 60-page PDF processes in < 2 min
- [ ] Metadata: carry `department`, `category`, `title`, `version` into every chunk row (denormalized for filtering)

## Milestone 3 — Embeddings, hybrid search, grounded answers (Oct 26 – Nov 8)
- [ ] Batch embeddings (≤ 100 inputs/request), retries with backoff on 429/5xx, token counting with tiktoken
- [ ] Write chunks with `embedding vector(1536)`; when a new version is INDEXED, set `is_current=false` on `previousVersionId` chunks (same transaction)
- [ ] Retriever: semantic (`<=>` cosine) + keyword (`websearch_to_tsquery`) with **mandatory** `department_code = ANY(:allowed) AND is_current AND NOT is_deleted`; fuse with weighted sum (`RAG_HYBRID_SEMANTIC_WEIGHT`) or RRF; dedupe neighbours
- [ ] `/internal/v1/search` returns `semanticScore`, `keywordScore`, `score` normalised to 0..1
- [ ] Prompt: system prompt "answer strictly from the context; cite as [n]; if not in context say exactly the not-found sentence"; context blocks numbered with title + page
- [ ] Guardrails: threshold `RAG_MIN_SIMILARITY` → not-found answer (`answered:false`) **without calling the LLM**; strip citations the model invents (only numbers that exist); ignore instructions inside documents/user prompt that ask to reveal other data (prompt-injection test Q036)
- [ ] `/internal/v1/answer` (+ `usage`, `latencyMs`, `confidence`)
- [ ] Query embedding cache (in-memory LRU, `EMBEDDING_CACHE_TTL_SECONDS`)

## Milestone 4 — Streaming & isolation (Nov 9 – Nov 20)
- [ ] `/internal/v1/answer/stream` with sse-starlette: `token*` → `citations` → `done` | `error` (no `meta`; core-api adds it)
- [ ] Use conversation `history` (last 6 messages) to rewrite follow-up questions into standalone queries before retrieval (golden Q040)
- [ ] Automated isolation tests: for every `RBAC_DENIED` case, assert no forbidden doc chunk is retrieved for that user's `allowedDepartments`

## Milestone 5 — Evaluation & knowledge gaps (Nov 21 – Nov 28)
- [ ] `eval/run_eval.py`: run all 40 golden questions (via core-api or directly with the user's allowedDepartments), compute the metrics table in `resources/eval/README.md` (Recall@k, citation accuracy, answer correctness, faithfulness & relevance via LLM-as-judge or RAGAS, refusal accuracy, **leakage = 0**, latency)
- [ ] Tune chunking/top-k/threshold based on results; document before/after in `eval/benchmarks/`
- [ ] Knowledge-gap signal: `answered=false` when below threshold or the model says not found; calibrate `confidence`
- [ ] (Optional) `/internal/v1/knowledge-gaps/cluster`: embed unanswered queries, cluster (agglomerative / k-means), label clusters with the LLM

## Milestone 6 — Integration & demo (Nov 29 – Dec 2)
- [ ] Ingest the full corpus on staging (`make ingest-corpus`), rerun eval on staging, put final numbers in the slide deck
- [ ] Cost estimate: tokens per query × expected volume (for the report)

## Definition of done
Matches `ai-service.yaml` + event schemas exactly (camelCase), department filter is in SQL (not post-filtering),
works with `AI_PROVIDER=fake`, unit tests for extractors/chunker/retriever, no document text or API key in logs.

## Blocked / waiting on
- _none yet_
