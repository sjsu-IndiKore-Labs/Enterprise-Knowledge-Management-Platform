---
from: changhyun
to: ayushi
status: open
priority: blocking
created: 2026-11-06
related: contracts/openapi/core-api.yaml, ayushi_backend/BACKEND_INTEGRATION_HANDOFF.md
---

# core-api (PR #6) differs from contracts v1: which one is the source of truth?

## Context
ADR-0003 says `contracts/` is the source of truth, and `pnpm gen:api` in the frontend CI generates types from
`contracts/openapi/core-api.yaml`. `BACKEND_INTEGRATION_HANDOFF.md` section 1.2 describes endpoints that differ from that file.
I read the handoff document, not the Java code, so please correct me where the code already matches the contract.

| # | Topic | Contract (`core-api.yaml`) | Handoff (core-api PR #6) |
|---|---|---|---|
| 1 | Upload path | `POST /api/v1/documents` | `POST /api/v1/documents/upload` |
| 2 | Upload form fields | `UploadDocumentForm` (file, title, department, category, author, tags) | file, title, department, **classification** |
| 3 | Status polling | `GET /api/v1/documents/{id}` returns `Document` | separate `GET /api/v1/documents/{id}/status` with `progressPercent` |
| 4 | Status values | `UPLOADED, QUEUED, PROCESSING, INDEXED, FAILED` | `PENDING, PROCESSING, INDEXED, FAILED` |
| 5 | Max upload size | 25 MB (`NEXT_PUBLIC_MAX_UPLOAD_MB=25`) | 50 MB |
| 6 | Department codes | `GENERAL, HR, ENGINEERING, FINANCE, LEGAL, MARKETING` | `HR, ENGINEERING, FINANCE, LEGAL, **OPERATIONS**, GENERAL` (no MARKETING) |
| 7 | List documents | paged `DocumentPage`, filters `department, category, status, q` | bare JSON array, no paging or filters |
| 8 | Search request | `{query, mode, topK, departments?, categories?}`; `departments` is **narrowed server-side** against the caller's `allowedDepartments` | `{query, authorizedDepartments, topK, minScore}` sent **by the client** |
| 9 | Search result | `content, score, semanticScore, keywordScore, version, sectionTitle, pageNumber` | `snippet, similarityScore, fileName, pageNumber` (no `version`, `sectionTitle` or mode scores) |

Item 8 is a security concern, not just a naming difference: if the server trusts a department list from the browser, any user can read
other departments' chunks by editing the request. The contract (`auth.md`, the RBAC matrix, `ACCESS_DENIED` → 404) assumes the server decides.

## Question
For each row, which side wins?
- A) Contract wins: core-api is changed to match `core-api.yaml` (my recommendation; the frontend, Prism mock and CI already follow it).
- B) Handoff wins: the contract is updated in the same PR, with a line in `contracts/CHANGELOG.md`, and I regenerate `schema.d.ts`.
- C) Per row (please mark each row).

For row 8, can the server derive allowed departments from the JWT and ignore (or intersect) anything the client sends?

## Proposed answer
Option A for rows 1 to 7 and 9, and server-side derivation for row 8. Until answered, the frontend is built against
`contracts/openapi/core-api.yaml` and the Prism mock (`make mock-core-api`). Every API call goes through one typed client module,
so switching later is a small change.

Frontend behaviour I am assuming, in line with the contract and the freeze draft: PDF and DOCX only, 25 MB, one file per upload,
poll `GET /documents/{id}` every 3 s until `INDEXED` or `FAILED`.

## Answer
<!-- Ayushi: decision per row, and whether contracts/ or the code changes. -->

## Acknowledgements
