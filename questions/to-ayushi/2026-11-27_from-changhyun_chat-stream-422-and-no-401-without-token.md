---
from: changhyun
to: ayushi
status: open
priority: normal
created: 2026-11-27
related: contracts/openapi/core-api.yaml, contracts/auth.md, contracts/streaming.md, questions/to-ayushi/2026-11-18_from-changhyun_login-me-stream-and-integration-findings.md
---

# `/chat/stream` ends in an error (ai-service answers 422) and requests without a token are accepted

## Context
Thank you for the answers of 2026-11-24: login, `/users/me` and `/chat/stream` exist on `develop` now, and the web app can use them.
I started current `develop` from scratch (`make reset && make up-apps`, `AI_PROVIDER=fake`, corpus uploaded as `alice.admin`) and tried the web flows.
The five core-api issues in Manali's `questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md` (missing `embedding` / `content_tsv`,
INDEXED event lost, DOCX 500, `OPERATIONS`, HTTP/1.1) are already tracked there, so I do not repeat them. Two more things showed up that are not on that list.

### 1. `POST /chat/stream` (and `POST /chat`) fail because ai-service answers 422
- Logged in as `hannah.hr@helios.example.com` (EMPLOYEE, HR): `POST /api/v1/chat/stream` with `{"message":"What is the minimum password length?"}` returns
  `event: error` with `code: STREAM_ERROR`, message `ai-service returned HTTP 422 from http://ai-service:8000/internal/v1/answer`.
  `POST /api/v1/chat` returns 502 for the same message.
- The ai-service log shows `POST /internal/v1/answer ... 422 Unprocessable Entity` (twice in this run). Some calls before that were 400 ("Unsupported upgrade request"), which is the HTTP/1.1 item above.
- When I send ai-service the payload I expect core-api to build (`queryId`, `query`, `allowedDepartments: ["HR","GENERAL"]`, `history: []`, `topK: 6`) with `X-Internal-Token`, it answers **200**.
  The same payload with `"history": null` answers **422** (`Input should be a valid list`). I could not read the body core-api really sends, so I do not know that `history` is the cause; it is only the one difference I could reproduce.
- `ChatService` builds `history` from `loadLast6HistoryMessages`, which looks non-null, so please check the real request (for example log the JSON body in `AiServiceClient` for one call).
- This blocks the whole chat screen against the real backend, with or without OpenAI.

### 2. Requests without `Authorization` are accepted
- `GET /api/v1/documents?size=50` **without a token** returns 200 with all 15 documents of every department (as the default dev ADMIN, `RequestIdFilter`).
  With hannah's token the same call returns only her 8 (HR + GENERAL), so the scoping itself works.
- `contracts/openapi/core-api.yaml` sets `security: [bearerAuth]` for every endpoint except `POST /auth/login`, and `contracts/auth.md` says the frontend clears the token on 401.
  Today the web app can never get a 401 from a missing or bad token, so the "session expired → `/login`" path cannot be tried against the real backend.

## Question
A. Where does the 422 come from? Please log the body core-api sends to `/internal/v1/answer` for one `/chat/stream` call, and tell me what you find.
B. Is returning 401 `UNAUTHENTICATED` for missing, invalid or expired tokens (the shared error body) planned before the demo rehearsal on 2026-11-30? If it will stay off until later, tell me the date, so I keep using it only as a known gap.

## Proposed answer
A. Fix on your side after reading the real request body (likely `history` or a department value). B. Turn the default-ADMIN fallback off outside a `dev` profile; keep an `X-User-*` override only for tests.

## Answer
<!-- Filled by the recipient: name, date, decision, what changes (contract? TODO?) -->
