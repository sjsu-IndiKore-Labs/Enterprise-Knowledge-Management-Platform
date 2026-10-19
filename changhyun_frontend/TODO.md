# TODO — Changhyun Kim (Frontend & UX)

> Agents: read [`/AGENTS.md`](../AGENTS.md) first. This file is Changhyun's plan; other agents read it for context.
> Tick `[x]` when done, add items you discover, and keep "Blocked / waiting on" current.

## Your role in one paragraph
You build the **web portal** employees and admins actually see: login, a ChatGPT-style assistant that streams answers
with clickable citation badges and a source preview, a document library with drag-and-drop upload and live processing
status, a search page, and an admin dashboard (usage, top questions, knowledge gaps, audit log, users). The browser talks
**only** to `core-api` (`NEXT_PUBLIC_API_BASE_URL`). Security decisions are made by the server; the UI only hides what the
user can't use (based on `GET /api/v1/users/me` → `permissions`).

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
- [ ] Acknowledge `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`; review `core-api.yaml` from a UI point of view (missing fields? ask Ayushi in `questions/to-ayushi/`)
- [ ] Wireframes for: login, chat (with citations + preview panel), document library, upload, document detail/versions, admin analytics, knowledge gaps → `wireframes/`
- [ ] User workflow diagram (employee asks a question; manager uploads; admin reviews gaps)
- [ ] Next.js + TS + Tailwind skeleton, ESLint + Prettier, `pnpm gen:api` script with `openapi-typescript`, typed client with `openapi-fetch`
- [ ] Layout: sidebar nav, top bar with user name/role/department, dark/light theme

## Milestone 2 — Upload (Oct 12 – Oct 25)
- [ ] Drag-and-drop upload (react-dropzone): accept `.pdf`/`.docx` only, max `NEXT_PUBLIC_MAX_UPLOAD_MB`, show client-side errors
- [ ] Upload form: title (prefilled from filename), department (only MANAGER's own dept; ADMIN any), category, author, tags
- [ ] Upload progress (XHR/axios `onUploadProgress`) then **processing status** polling `GET /documents/{id}` every 3 s until `INDEXED`/`FAILED` (stop polling on unmount)
- [ ] Document library: table with filters (department, category, status, text), paging, status badges, error message tooltip for `FAILED`
- [ ] Document detail: metadata, versions list, "upload new version", open file via `download-url`
- [ ] Render the shared error body (`code`, `message`) in toasts; 401 → redirect to `/login`

## Milestone 3 — Search (Oct 26 – Nov 8)
- [ ] Search page calling `POST /api/v1/search` with mode toggle (Hybrid / Semantic / Keyword), result cards with highlighted terms, page number, department chip, score
- [ ] Citation preview component (reused by chat): `GET /documents/{id}/chunks/{chunkId}` + "Open document at page N" (`download-url?page=N`)

## Milestone 4 — Auth & chat (Nov 9 – Nov 20)
- [ ] Login page → `POST /api/v1/auth/login`; token in memory + `sessionStorage`; auth context; route guards; logout
- [ ] Hide/disable UI by `permissions` from `/users/me` (upload only for DOCUMENT_WRITE, admin pages only for ANALYTICS_READ/USER_ADMIN)
- [ ] Chat UI: conversation list (sidebar), new chat, message list, Markdown rendering (react-markdown, sanitized), auto-scroll
- [ ] **Streaming**: `fetch` + `ReadableStream` SSE parser for `meta / token / citations / done / error` (no `EventSource`), stop button (AbortController)
- [ ] Replace `[n]` markers with clickable citation badges → side panel preview
- [ ] "Not found in your documents" state for `answered: false` (friendly, suggests contacting the owner team)
- [ ] Thumbs up/down feedback per answer

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
- _none yet_
