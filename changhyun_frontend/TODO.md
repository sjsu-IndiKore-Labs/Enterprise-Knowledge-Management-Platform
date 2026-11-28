# TODO — Changhyun Kim (Frontend & UX)

> Agents: read [`/AGENTS.md`](../AGENTS.md) first. This file is Changhyun's plan; other agents read it for context.
> Tick `[x]` when done, add items you discover, and keep "Blocked / waiting on" current.

## Your role in one paragraph
You build the **web portal** employees and admins actually see: login, a ChatGPT-style assistant that streams answers
with clickable citation badges and a source preview, a document library with drag-and-drop upload and live processing
status, a search page, and an admin dashboard (usage, top questions, knowledge gaps, audit log, users). The browser talks
**only** to `core-api` (`NEXT_PUBLIC_API_BASE_URL`). Security decisions are made by the server; the UI only hides what the
user can't use (based on `GET /api/v1/users/me` → `permissions`).

## Status (2026-11-27)
- **M1-M4 are merged into `develop`** (PR #35 and #36, approved 2026-11-22/23). Ayushi (PR #39) and Vedanti (PR #37, #41) merged their work too, so `develop` has login, `/users/me`, `/chat/stream`, the admin endpoints and a real ai-service. Manali's staging is up at `http://ekm-staging-800774594.us-west-2.elb.amazonaws.com` (PR #44 still open); chat there waits for the OpenAI key.
- **First run of the whole `develop` stack (2026-11-27):** `make reset && make up-apps` with `AI_PROVIDER=fake`, corpus uploaded as `alice.admin`. Works: real login and `/users/me` (hannah gets `allowedDepartments: [HR, GENERAL]`, `permissions: [DOCUMENT_READ]`), document list scoped by JWT (hannah 8, no token 15), ai-service `/health` with real DB state. Does not work yet, all on Ayushi's side: indexing fails (`embedding` / `content_tsv` missing in Flyway), `INDEXED` is never applied (`@Transactional` self-invocation), DOCX upload 500, search 400 / chat 502 (HTTP/2 upgrade), `/chat/stream` ends in `error` (ai-service answers 422), and requests without a token are accepted. The first five are in Manali's question to Ayushi; the last two are in [`questions/to-ayushi/2026-11-27_from-changhyun_chat-stream-422-and-no-401-without-token.md`](../questions/to-ayushi/2026-11-27_from-changhyun_chat-stream-422-and-no-401-without-token.md). Fixes are promised by 2026-11-29, staging rehearsal 2026-11-30.
- **Web image:** `make up-apps` failed on the web build because the Dockerfile did not pin pnpm (corepack took pnpm 12, which blocks esbuild's install script). Fixed with `"packageManager": "pnpm@9.15.9"` in PR #45.
- **Next:** M5 (admin screens) can be built against the real admin endpoints, which return 200 locally; M6 follows. Manali's `questions/to-changhyun/2026-10-19_from-manali_nextjs-container-requirements.md` is already satisfied (`output: 'standalone'`, `/api/health`, empty `NEXT_PUBLIC_API_BASE_URL` gives same-origin URLs, lockfile committed); answer it once PR #44 is merged.

## Earlier status (2026-11-18)
- **M4 written and checked by hand against the mock server (2026-11-18).** Steps 10-17 of the README "Manual check of M4" were walked through in a real browser against `pnpm mock`: streaming answer with citation badges and sources, side panel, Stop / "Ask again" / broken stream / 502, not-found state, thumbs up and down (kept after reloading the conversation), history in the sidebar (follow-up stays in one conversation, delete asks first), keyboard (Enter, Shift+Enter, Enter during IME composition does not send), permissions and a clean history for the next user. `pnpm lint`, `typecheck`, `test` (64), `mock:test` (30) and `build` pass. As with M2/M3 this proves the screens against the contract-shaped mock, not against the real backend: `/auth/login`, `/users/me` and `/chat/stream` are not in core-api yet (see "Blocked / waiting on").
- **First run against the real core-api (2026-11-18, partial):** Ayushi's branch `origin/ayushi/m1-m3-ingestion-search-chat` was run locally (Docker postgres + LocalStack, Java 21) behind `pnpm mock:proxy`. Document list, upload (202, `QUEUED`), new version, download URL and the error bodies match the contract; search and chat fail (no ai-service), login, `/users/me` and `/chat/stream` do not exist yet. Details in the two questions under "Blocked / waiting on".
- **UI restyle (2026-11-18):** calm document-tool look (neutral palette, one indigo accent, floating sidebar with Search/Ask, Documents, Chats, Departments and Upload; status tiles and pill filters on the document list). The chat history moved from a column inside the assistant page to the sidebar; chat state lives in `ChatSessionProvider`. No API calls or contracts changed.
- **App code: M1-M3 written; build checks pass.** On 2026-11-06 (Mac, Node 23, pnpm 9) `pnpm install`, `pnpm gen:api`, `pnpm lint`, `pnpm typecheck`, `pnpm test` and `pnpm build` all pass; `pnpm-lock.yaml` and `src/lib/api/schema.d.ts` are committed, so the CI `frontend` job (`pnpm install --frozen-lockfile`, pnpm 9) has what it needs. M2 was checked by hand on 2026-11-06 against the stateful mock server (`pnpm mock`, steps 1-6 of the README checklist): login and reload, role rules, upload validation, QUEUED → PROCESSING → INDEXED without reload and FAILED with reason, library filters and paging, detail and new version, session expiry back to `/login`. M3 was checked by hand the same way (search modes, highlighting, passage preview, "Open at page N"). Switching mode or a filter after a search now re-runs it, which was found during that check. The mock follows the contract, not Ayushi's current controllers, so none of this proves the real backend works yet.
- A minimal login page and auth context were pulled forward from M4, because the API needs a Bearer token and "401 → /login" is an M2 item.
- **Interactive prototype added:** [`wireframes/ekm-frontend-prototype.html`](wireframes/ekm-frontend-prototype.html) (see [`wireframes/README.md`](wireframes/README.md)). It covers upload with status polling, document library, document detail and versions, search with modes, and the citation preview, against mock data shaped like `core-api.yaml`. It is a UX reference for the team meeting, not production code.
- **Proposed decisions (confirm with the team):**
  - One file per upload, matching `POST /api/v1/documents` (one `file` plus one metadata set). Multi-file selection is out of scope.
  - PDF and DOCX only, 25 MB (`NEXT_PUBLIC_MAX_UPLOAD_MB=25`). The earlier mockup allowed `.doc` and 20 MB; that changes.
  - Citation preview shows the chunk text from `GET /documents/{id}/chunks/{chunkId}` plus "Open document at page N" via `download-url?page=N`. DOCX has no pages, so it shows the section title.
  - Package manager is **pnpm** (CI requires it). `requirement-freeze/changhyun_frontend_requirement_freeze_DRAFT.md` still says `npm run dev`; update it when agreed.
- **Next:** run M1-M3 against the Prism mock and fix what shows up, then align with Ayushi's answer (see "Blocked / waiting on"), then M4 (chat, streaming, citations, permissions).

## You PROVIDE
| To | What |
|---|---|
| Everyone (demo) | The UI for every demo flow in `docs/demo-script.md` |
| Manali | `Dockerfile` (multi-stage, `node:20-alpine`, Next standalone output), `GET /api/health`, build-time `NEXT_PUBLIC_*` vars |
| Vedanti / Ayushi | Feedback (thumbs up/down) wired to `POST /api/v1/chat/messages/{id}/feedback` → used in RAG evaluation |

## You CONSUME
| From | What | Until it's ready, use |
|---|---|---|
| Ayushi | `contracts/openapi/core-api.yaml` (all `/api/v1/**`) | **Prism mock**: `make mock-core-api` → `http://localhost:4010` |
| Ayushi + Vedanti | SSE chat stream (`contracts/streaming.md`) | a local mock stream (`src/mocks/chatStream.ts`) that emits the exact event sequence from `contracts/streaming.md` |
| Resources | Sample PDFs/DOCX in `resources/corpus/files`, edge cases in `resources/edge-cases` | for upload testing |

## Milestone 1 — catch-up (was due Oct 11) — finish by **Oct 21**
- [x] Acknowledge `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`; review `core-api.yaml` from a UI point of view (missing fields? ask Ayushi in `questions/to-ayushi/`) — acknowledged 2026-11-06; contract differences sent to Ayushi (see "Blocked / waiting on")
- [x] Wireframes for: login, chat (with citations + preview panel), document library, upload, document detail/versions, admin analytics, knowledge gaps → `wireframes/` — library, upload, detail/versions, search and the preview panel in `ekm-frontend-prototype.html`; login, chat, admin analytics and knowledge gaps in `ekm-wireframes-m4-m5.html` (audit log and users page come with M5)
- [x] User workflow diagram (employee asks a question; manager uploads; admin reviews gaps) → `wireframes/user-workflows.md`
- [x] Next.js + TS + Tailwind skeleton, ESLint + Prettier, `pnpm gen:api` script with `openapi-typescript`, typed client with `openapi-fetch`
- [x] Layout: sidebar nav, top bar with user name/role/department, dark/light theme

## Milestone 2 — Upload (Oct 12 – Oct 25)
- [x] Drag-and-drop upload (react-dropzone): accept `.pdf`/`.docx` only, max `NEXT_PUBLIC_MAX_UPLOAD_MB`, show client-side errors
- [x] Upload form: title (prefilled from filename), department (only MANAGER's own dept; ADMIN any), category, author, tags
- [x] Upload progress (XHR/axios `onUploadProgress`) then **processing status** polling `GET /documents/{id}` every 3 s until `INDEXED`/`FAILED` (stop polling on unmount)
- [x] Document library: table with filters (department, category, status, text), paging, status badges, error message tooltip for `FAILED`
- [x] Document detail: metadata, versions list, "upload new version", open file via `download-url`
- [x] Render the shared error body (`code`, `message`) in toasts; 401 → redirect to `/login`

## Milestone 3 — Search (Oct 26 – Nov 8)
- [x] Search page calling `POST /api/v1/search` with mode toggle (Hybrid / Semantic / Keyword), result cards with highlighted terms, page number, department chip, score
- [x] Citation preview component (reused by chat): `GET /documents/{id}/chunks/{chunkId}` + "Open document at page N" (`download-url?page=N`)

## Milestone 4 — Auth & chat (Nov 9 – Nov 20)
- [x] Login page → `POST /api/v1/auth/login`; token in memory + `sessionStorage`; auth context; route guards; logout
- [x] Hide/disable UI by `permissions` from `/users/me` (upload only for DOCUMENT_WRITE; the admin pages come with M5 and will use ANALYTICS_READ/USER_ADMIN)
- [x] Chat UI: conversation list (sidebar), new chat, message list, Markdown rendering (own small parser instead of react-markdown: http(s)/mailto links only, raw HTML stays text), auto-scroll
- [x] **Streaming**: `fetch` + `ReadableStream` SSE parser for `meta / token / citations / done / error` (no `EventSource`), stop button (AbortController)
- [x] Replace `[n]` markers with clickable citation badges → side panel preview
- [x] "Not found in your documents" state for `answered: false` (friendly, suggests contacting the owner team)
- [x] Thumbs up/down feedback per answer

## Milestone 5 — Admin analytics (Nov 21 – Nov 28)
- [ ] Dashboard (Recharts): KPI tiles from `/admin/analytics/summary`, queries-over-time line chart, queries by department, top questions, top cited documents
- [ ] Knowledge gaps table with status workflow (OPEN → IN_PROGRESS → RESOLVED/DISMISSED) and notes
- [ ] Audit log viewer with filters; Users admin page (create user, change role/department, deactivate)

## Milestone 6 — Polish & demo (Nov 29 – Dec 2)
- [ ] Responsive (mobile chat), accessibility pass (keyboard nav, aria labels, contrast), empty/loading/error states everywhere
- [ ] Playwright smoke test: login → ask Q001 from `resources/eval/golden_qa.jsonl` → see citation
- [ ] Screenshots for the final slide deck

## Definition of done (every screen)
Uses generated types (no hand-written API types), handles loading/empty/error, works against the Prism mock **and** the real backend,
no secrets or tokens in `localStorage`/logs.

## Blocked / waiting on
- Ayushi: [`questions/to-ayushi/2026-11-27_from-changhyun_chat-stream-422-and-no-401-without-token.md`](../questions/to-ayushi/2026-11-27_from-changhyun_chat-stream-422-and-no-401-without-token.md) — `/chat/stream` and `/chat` fail because ai-service answers 422; requests without a token are accepted (no 401). Also waiting on the five core-api fixes in Manali's `questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md` (on PR #44) before uploads reach `INDEXED` and search works. Until then the web app can use the real login and the document screens; search and chat still need `pnpm mock`.
- Vedanti: [`questions/to-vedanti/2026-11-18_from-changhyun_fake-ai-service-eta.md`](../questions/to-vedanti/2026-11-18_from-changhyun_fake-ai-service-eta.md) — the service is merged (PR #37, #41), so the ETA question is answered by the code; the file still needs her Answer. One open point: with `AI_PROVIDER=fake` a basic question such as "unused PTO carry-over" answers "not found" (confidence about 0.03-0.11, below `RAG_MIN_SIMILARITY`), so a demo without an OpenAI key shows no citations.
- Manali: PR #44 (staging, `questions/to-changhyun/...nextjs-container-requirements.md`, the to-all reset notice). Answer and acknowledge after it is merged. PR #45 (pnpm pin) needs one approval.

Answered, kept for reference:
- Ayushi, 2026-11-24: login, `/users/me`, `/chat/stream` and the 400 for an invalid enum filter ([`2026-11-18` question](../questions/to-ayushi/2026-11-18_from-changhyun_login-me-stream-and-integration-findings.md)); contract wins on every row of [`2026-11-06`](../questions/to-ayushi/2026-11-06_from-changhyun_core-api-differs-from-contract.md).
- Branching target: `develop` is the base (PR #35 and #36 were merged into it).
