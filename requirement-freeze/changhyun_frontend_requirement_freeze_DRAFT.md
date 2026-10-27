# Frontend Requirement Freeze

**Enterprise Knowledge Management Platform**

|  |  |
| --- | --- |
| Project | Enterprise Knowledge Management Platform |
| Scope owner | Changhyun Kim, Frontend and UX |
| Proposed freeze date | October 27, 2026 (draft for team agreement) |
| Target demonstration | December 2, 2026 |

This document defines the Frontend and UX MVP scope for the December 2, 2026 demonstration.

## Objective

Provide a responsive web frontend (Next.js, TypeScript, Tailwind CSS) that lets a user upload and track documents, search with source previews, sign in and chat with cited answers, and lets an administrator review analytics, knowledge gaps, and audit logs. The frontend consumes the team API contracts; backend, AI/RAG, and DevOps owners remain responsible for their own business logic and infrastructure.

## In Scope

Priorities: **MUST** protects the demo path, **SHOULD** is expected if time allows, **COULD** is dropped first.

**Foundation (M1)**

- Next.js project structure, ESLint/Prettier, typed API client generated from `contracts/openapi/core-api.yaml`.
- Responsive layout and navigation shell.

**Document ingestion (M2)**

- MUST: drag-and-drop and file selection for `.pdf` and `.docx` only, with client validation and a 25 MB limit.
- MUST: metadata form with title, department, and category required; author and tags optional.
- MUST: multipart `POST /api/v1/documents` with upload progress and handling of shared errors (401, 403, 413, 415).
- MUST: poll `GET /api/v1/documents/{id}` every 3 seconds and show QUEUED → PROCESSING → INDEXED / FAILED.
- SHOULD: document library with filters, paging, and status badges.
- COULD: document detail, versions, new-version upload, and download-link UI.

**Search and citations (M3)**

- Search page with Hybrid, Semantic, and Keyword modes, ranked and highlighted results.
- Reusable citation preview with document and page link (preview approach to be decided).

**Authentication and chat (M4)**

- Login and logout, frontend token state, protected routes, and permission-based UI.
- Streaming chat with history, stop control, Markdown rendering, and clickable citation badges.

**Admin (M5)**

- Analytics dashboard, knowledge-gap workflow, and audit-log viewer.
- COULD: user administration screens (not in the Project Abstract).

**Quality and demo (M6)**

- Loading, empty, and error states on every screen; responsive and accessibility fixes done incrementally during M3–M5.
- Playwright smoke test for the demo path: upload → status → search → chat and citation → admin.
- Screenshots and demo support.

**Packaging**

- Dockerfile, health endpoint, and run commands for the web frontend so DevOps can build and deploy it.

## Out of Scope

- JWT issuance and validation, server-side authorization, streaming relay, RAG answer generation, vector indexing, and ranking algorithms.
- AWS infrastructure, CI/CD pipelines, and deployment (DevOps scope).
- Additional document formats beyond PDF and DOCX.
- Document editing or authoring inside the browser.
- Multi-language UI and offline mode.
- Native mobile apps; the web UI is responsive only.
- Browser support beyond the current version of Chrome (other browsers are best effort).
- Formal accessibility certification; only a basic keyboard and contrast check is planned.

## Acceptance Criteria

- [ ] Upload accepts only PDF and DOCX up to 25 MB, submits the required metadata, shows progress, and reflects QUEUED → PROCESSING → INDEXED / FAILED against the Prism mock (and the real API when available).
- [ ] Search returns ranked results with a working citation preview against the mock or real search API.
- [ ] Login leads to protected routes and role-based UI.
- [ ] Streaming chat shows incremental answers, a working stop control, and clickable citations.
- [ ] Admin screens render contract-shaped data with empty, loading, and error states.
- [ ] The frontend builds in Docker, passes lint and type checks in CI, and answers its health check.
- [ ] The Playwright smoke test passes for the demo path.
- [ ] Running the frontend, its environment variables, and known limitations are documented.

## Inputs the Frontend Provides to DevOps

| Required input | Frontend status |
| --- | --- |
| Source code | `changhyun_frontend/` in the team repository (upload mockup not yet pushed) |
| Dockerfile | To be added with the first frontend pull request |
| Port and health endpoint | Next.js default port 3000 and a simple health route, both to be confirmed |
| Startup and test commands | `npm run dev`, `build`, `start`, lint and type check; Playwright added in M6 |
| Environment variables | API base URL and similar settings only; no secrets in the frontend |
| External dependencies | Core backend API (AI/RAG reached through it); Prism mock during development |

## Dependencies From Service Owners

| Owner | Required input | Needed by (proposed) |
| --- | --- | --- |
| Ayushi (Backend) | OpenAPI contract and shared error format | Now |
| Ayushi (Backend) | Upload and status endpoints (Prism mock is acceptable) | M2, now |
| Ayushi (Backend) | Search API | Shape by M3 start, live by M3 end |
| Ayushi (Backend) | Auth endpoints and token claims (role, department); a dev or mock token before login lands | M4 start |
| Ayushi (Backend) | Analytics, knowledge-gap, and audit endpoints; user-management endpoints only if that item is kept | M5 start |
| Vedanti (AI/RAG) | Citation data shape (document, page or chunk) | M3 start |
| Vedanti (AI/RAG) | Streaming message format | M4 start |
| Vedanti (AI/RAG) | Knowledge-gap detection output | M5 start |
| Manali (DevOps) | Prism mock, CI workflow, environment configuration, staging URL and CORS settings | Now; staging by M6 |

## Open Decisions to Close Before Freeze

- Upload flow: one file per upload with its metadata, or several files with shared metadata.
- Citation preview: PDF page render or chunk text (DOCX has no page numbers).
- Streaming protocol and message format, following contracts-v1.
- Dev or mock token for upload and search calls before login is built.
- Whether user-management screens stay in M5 or are dropped.
- Owner of the final slide deck and project documentation.

## Freeze Rule

New screens, features, libraries, or design directions require team agreement and must not delay the agreed MVP. Any proposed addition must identify its value, owner, time cost, and impact on the December 2 demonstration. If time runs short, COULD items are cut first, then SHOULD items, before any MUST item.

## Decision

This scope is the baseline for frontend implementation and team coordination. The service owners may continue to develop their application features, but their API contracts must remain compatible with this freeze; the frontend adapts through the generated typed client.
