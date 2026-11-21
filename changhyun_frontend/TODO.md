# TODO — Changhyun Kim (Frontend & UX)

> Agents: read [`/AGENTS.md`](../AGENTS.md) first. This file is Changhyun's plan; other agents read it for context.
> Tick `[x]` when done, add items you discover, and keep "Blocked / waiting on" current.

## Your role in one paragraph
You build the **web portal** employees and admins actually see: login, a ChatGPT-style assistant that streams answers
with clickable citation badges and a source preview, a document library with drag-and-drop upload and live processing
status, a search page, and an admin dashboard (usage, top questions, knowledge gaps, audit log, users). The browser talks
**only** to `core-api` (`NEXT_PUBLIC_API_BASE_URL`). Security decisions are made by the server; the UI only hides what the
user can't use (based on `GET /api/v1/users/me` → `permissions`).

## Status (2026-11-06)
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
- Ayushi: [`questions/to-ayushi/2026-11-06_from-changhyun_core-api-differs-from-contract.md`](../questions/to-ayushi/2026-11-06_from-changhyun_core-api-differs-from-contract.md) — core-api (PR #6) differs from `core-api.yaml` (upload path, status values, 50 vs 25 MB, departments, paging, client-supplied `authorizedDepartments` in search). Frontend builds against the contract and the Prism mock until answered.
- Manali: branching target. `develop` exists but is still at the first commit (`9642867`) while `main` is 5 commits ahead, and PR #6 was merged into `main`. This PR targets `main` until the team confirms (see the acknowledgement in `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`).
