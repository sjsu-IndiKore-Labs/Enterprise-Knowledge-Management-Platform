---
from: changhyun
to: vedanti
status: open
priority: blocking
created: 2026-11-18
related: contracts/openapi/ai-service.yaml, contracts/streaming.md, vedanti_ai/TODO.md
---

# Can a minimal ai-service (AI_PROVIDER=fake) be pushed this week? Search, chat and indexing all wait on it

## Context
I ran Ayushi's core-api branch (`origin/ayushi/m1-m3-ingestion-search-chat`) locally with Postgres + LocalStack and the web app on top.
Everything that does not need the AI side works (document list, upload, versions, download). Three things stop at the same place,
because nothing answers on `AI_SERVICE_URL` (:8000):
- `POST /api/v1/search` returns 502 `UPSTREAM_UNAVAILABLE` (core-api cannot reach `/internal/v1/search`).
- chat answers (and later `/chat/stream`) cannot work without `/internal/v1/answer` and `/internal/v1/answer/stream`.
- uploaded documents stay `QUEUED` forever: there is no worker that reads the ingest queue and reports `PROCESSING` / `INDEXED` / `FAILED`.

On `origin/vedanti_ai` (last push 2026-11-06) I only see the ingestion foundation (`app/ingestion/chunker.py`, `extract.py`, `models.py`,
2 tests): no FastAPI app, no `/health`, no SQS worker. Your `TODO.md` has no boxes ticked, so I cannot tell what is already done locally.

## Question
A. Is there a FastAPI app (even partly working) that is not pushed yet? Please push your branch, even if it is rough.
B. Can you push a **minimal** service first (`GET /health`, `X-Internal-Token` check, the shared error body, `/internal/v1/search`,
   `/internal/v1/answer`, `/internal/v1/answer/stream` returning canned or `FakeEmbeddings`/`FakeLLM` results in the exact shapes of
   `ai-service.yaml` and `streaming.md`), and the SQS worker that sends `PROCESSING` then `INDEXED` (fake chunk count is fine)?
   The real retrieval can replace the fake one behind the same endpoints later.
C. What date can you commit to for B, and for the real hybrid search (your M3)?

## Proposed answer
B this week (by 2026-11-20): it unblocks Ayushi's search/chat proxy, the streaming relay, and my demo screens without any of the real
RAG work. Until then I use my mock server for search and chat; if it helps, `make mock-ai-service` (Prism on :4011) can stand in for
core-api's calls. Please also tick what is done in `vedanti_ai/TODO.md` so the status is visible to everyone.

## Answer
<!-- Filled by the recipient: name, date, decision, what changes (contract? TODO?) -->
