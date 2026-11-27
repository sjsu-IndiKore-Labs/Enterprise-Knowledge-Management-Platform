---
from: changhyun
to: ayushi
status: answered
priority: normal
created: 2026-11-18
related: contracts/openapi/core-api.yaml, contracts/auth.md, contracts/streaming.md, ayushi_backend/TODO.md (Milestone 4)
---

# When can the web app log in against core-api? (login, /users/me, /chat/stream) + findings from a first integration run

## Context
Frontend M4 (login, streaming chat, citations, feedback, permission-based menus) is finished and checked by hand against my
stateful mock server (`pnpm mock` in `changhyun_frontend/`). To find mismatches early I also ran your branch
`origin/ayushi/m1-m3-ingestion-search-chat` (commit `c33f0c8`) locally: Postgres + LocalStack from `make up`, `make seed`,
Java 21, `core-api` on :8080, and the web app talking to it through a small dev-only proxy that fakes just `POST /auth/login`
and `GET /users/me` and forwards everything else to :8080 unchanged. I did not change any of your files.

What works against the real core-api (matches the contract): `GET /departments`, `GET /documents` (paging, `q`, `sort`, filters),
`POST /documents` (202, `QUEUED`), `GET /documents/{id}` with `versions`, `POST /documents/{id}/versions`, `GET .../download-url`,
`GET /conversations`, and the error bodies for 400 (`VALIDATION_ERROR`), 415 and 404. Several rows of my 2026-11-06 question
(upload path, paging, status values) look resolved on this branch, thank you.

What I could not exercise, and what I found:
1. There is no `POST /auth/login` and no `GET /users/me`, and requests without a token are accepted (`GET /documents` without
   `Authorization` returns 200, `createdBy` is always `00000000-0000-0000-0000-000000000001`). I expect this until your M4, so this is
   only a heads-up that the web app is waiting on it.
2. `POST /chat/stream` does not exist. It answers **500 `INTERNAL_ERROR`**, not 404.
3. `GET /documents?status=BOGUS` (any invalid enum value) answers **500 `INTERNAL_ERROR`**; the contract format says 400 `VALIDATION_ERROR`.
4. Document responses carry extra fields not in `core-api.yaml`: `fileName`, `fileType`, `fileSize`, `classification`, `version`.
   The web app ignores them, so this is harmless, but they should be in the contract or removed.
5. `POST /search` and chat answers return 502 `UPSTREAM_UNAVAILABLE` because there is no ai-service yet (question to Vedanti is separate).

## Question
A. Are `POST /auth/login`, JWT validation and `GET /users/me` (with `allowedDepartments` and `permissions`) still planned for
   2026-11-20? If it slips, which day should I plan for? Please push them to a branch as soon as login works, even before the rest of M4.
B. Same for `POST /chat/stream`: the frontend expects exactly the events of `contracts/streaming.md` (`meta`, `token`*, `citations`, `done` | `error`).
C. Can the M1-M3 branch be merged into `develop` now (or tell me the PR number), so everyone tests the same code?
D. Items 3 and 4 above: fix in code, or change the contract (contract changes go in their own PR with a `CHANGELOG.md` line)?

## Proposed answer
A/B: login and `/users/me` by 2026-11-20 and the stream relay by 2026-11-24. Until then I keep using the dev proxy (`pnpm mock:proxy`),
which is not a security test. C: yes, merge. D: fix 3 in code (400 `VALIDATION_ERROR`), and add the extra fields to the contract
only if you want to keep them.

## Answer
**Ayushi (2026-11-24):**
- **A (`POST /api/v1/auth/login` & `GET /api/v1/users/me`):** Completed and pushed on `ayushi/m1-m3-ingestion-search-chat`. All 12 dev users from `resources/seed/users.json` (password `Password123!`) are seeded automatically by Flyway `V3__m4_m5_auth_audit_analytics.sql`, HS256 JWTs match `contracts/auth.md`, and `/api/v1/users/me` returns `allowedDepartments` and `permissions`.
- **B (`POST /api/v1/chat/stream`):** Completed and pushed. Streams `event: meta` -> `event: token` -> `event: citations` -> `event: done` (or `event: error`) matching `contracts/streaming.md`.
- **C (Merge to `develop`):** Branch `ayushi/m1-m3-ingestion-search-chat` has been rebased onto latest `origin/develop` and is ready to merge.
- **D (Invalid enum query param -> 400):** Added `MethodArgumentTypeMismatchException` handler in `GlobalExceptionHandler` so invalid enum parameters like `GET /api/v1/documents?status=BOGUS` return `400 VALIDATION_ERROR`.
