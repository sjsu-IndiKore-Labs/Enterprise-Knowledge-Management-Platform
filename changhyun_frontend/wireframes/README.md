# wireframes/ — Frontend & UX

## `ekm-frontend-prototype.html`

A single-file, clickable prototype of the web portal. Open it in a browser; there is nothing to install.
It runs entirely in the browser with mock data shaped like `contracts/openapi/core-api.yaml`
(camelCase fields, UPPER_SNAKE_CASE enums, the shared error codes). It makes no network calls.
Fonts load from Google Fonts when online and fall back to system fonts offline.

This is a design and UX reference, not production code. The real app (Next.js + TypeScript + Tailwind) follows `../README.md`.

### What it covers

| Milestone | Screen | What to look at |
|---|---|---|
| M2 | Upload | One file per upload, PDF/DOCX only, 25 MB limit, required metadata, progress bar, then QUEUED → PROCESSING → INDEXED / FAILED polling every 3 s |
| M2 | Documents | Filters (text, department, category, status), paging, status badges, FAILED reason on hover, auto-refresh while documents are processing |
| M2 | Document detail | Metadata, version list, upload new version, "Open file" (presigned URL) |
| M3 | Search | Hybrid / Semantic / Keyword toggle, highlighted terms, page or section label, score |
| M3 | Citation preview | Chunk text drawer with "Open document at page N"; DOCX shows the section instead of a page |
| All | Role switcher | Alice (ADMIN), Harper (MANAGER, HR), Hannah (EMPLOYEE, HR), Eric (EMPLOYEE, ENGINEERING) from `resources/seed/users.json` |

Not covered yet: login, chat with streaming and citation badges (M4), admin analytics, knowledge gaps and audit log (M5).

### Five-minute walkthrough

1. **Documents** as Alice. Point out the PROCESSING document that turns INDEXED on its own, and hover the FAILED one for the reason.
2. **Upload** as Harper. Drop `resources/edge-cases/EDGE-003_unsupported_format.txt` to see the rejection, then a real PDF or DOCX. Watch the status steps. Department is locked to HR.
3. **Search** as Alice. Run "vacation rollover" in Keyword (no results), then Semantic (finds the PTO carryover rule). Open the result to see the citation preview.
4. **Search** a DOCX source ("benefits enrollment deadline"). The preview shows the section because DOCX has no pages.
5. **Access rules**. Switch to Eric and search "salary bands 2026": the HR compensation document never appears. Switch to Hannah: the Upload item disappears.

### Mocked on purpose

Search ranking is a small keyword and synonym matcher over 15 text chunks copied from `resources/corpus/source/`.
Backend processing is a 3-second timer. File type checks use the extension only; the real backend validates content and size.

## More wireframes and workflows (M1 completion)

- [`ekm-wireframes-m4-m5.html`](ekm-wireframes-m4-m5.html): open in a browser. Four tabs: **Login** (idle, busy, wrong password, session expired), **Chat** (answered with citation badges and a source preview panel, streaming with a Stop button, "not found", error), **Admin analytics** (KPI tiles, queries over time, by department, top questions and documents) and **Knowledge gaps** (filter, manage panel with status and notes). Data is mock but uses the field names from `core-api.yaml` and `contracts/streaming.md`; the notes under each screen name the endpoint behind each element.
- [`user-workflows.md`](user-workflows.md): three flowcharts (employee asks a question, manager uploads a document, admin reviews knowledge gaps) and a table of who sees what. GitHub renders the Mermaid diagrams.

Not drawn yet (planned for M5): audit log viewer and the users admin page.
