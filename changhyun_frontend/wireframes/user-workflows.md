# User workflows

Three flows cover the demo: an **employee** asks a question, a **manager** uploads a document, an **admin** closes knowledge gaps.
Endpoint names come from [`contracts/openapi/core-api.yaml`](../../contracts/openapi/core-api.yaml); the stream format is in [`contracts/streaming.md`](../../contracts/streaming.md).
Screens are in [`ekm-frontend-prototype.html`](ekm-frontend-prototype.html) (library, upload, detail, search) and [`ekm-wireframes-m4-m5.html`](ekm-wireframes-m4-m5.html) (login, chat, analytics, gaps).

## 1. Employee asks a question

```mermaid
flowchart TD
  A([Open portal]) --> B{Signed in?}
  B -- no --> L[Login<br/>POST /auth/login]
  L -- wrong password --> L
  L -- ok --> C
  B -- yes --> C[Chat<br/>GET /users/me, GET /conversations]
  C --> D[Type a question<br/>POST /chat/stream]
  D --> E[Answer streams in<br/>meta, token, citations, done]
  E --> F{answered?}
  F -- yes --> G[Answer with citation badges 1, 2, ...]
  G --> H[Click a badge<br/>GET /documents/id/chunks/chunkId]
  H --> I[Preview panel shows the passage]
  I --> J[Open at page N<br/>GET /documents/id/download-url?page=N]
  G --> K[Thumbs up or down<br/>POST /chat/messages/id/feedback]
  F -- no --> M["Not found" message<br/>question recorded as a knowledge gap]
  M --> N[Rephrase, or ask the owning team]
  E -. error event .-> O[Friendly error with Retry]
  D -. session expired, 401 .-> L
```

## 2. Manager uploads a document

```mermaid
flowchart TD
  A([Manager opens Documents]) --> B{Role MANAGER or ADMIN?}
  B -- no --> X[Upload hidden, server also returns 403]
  B -- yes --> C[Upload page]
  C --> D[Drop one PDF or DOCX]
  D --> E{Valid type and size?}
  E -- no --> F[Inline error<br/>wrong type, empty, over 25 MB]
  F --> D
  E -- yes --> G[Form: title prefilled, department, category, author, tags]
  G --> H{Department allowed?}
  H -- "MANAGER: own department only" --> I[Submit<br/>POST /documents, multipart]
  H -- "ADMIN: any department" --> I
  I --> J[Upload progress bar]
  J -- 202 accepted --> K[Processing tracker<br/>GET /documents/id every 3 s]
  J -- 4xx or 5xx --> T[Error toast with server message]
  K --> L{Status}
  L -- QUEUED or PROCESSING --> K
  L -- INDEXED --> M[Ready: searchable and citable]
  L -- FAILED --> N[Reason shown, upload again as a new version]
  M --> O[Later: Upload new version<br/>POST /documents/id/versions]
  O --> K
```

## 3. Admin reviews knowledge gaps

```mermaid
flowchart TD
  A([Admin opens Analytics]) --> B[KPI tiles and charts<br/>GET /admin/analytics/summary, queries-over-time]
  B --> C[Top questions with low answered rate<br/>GET /admin/analytics/top-queries]
  B --> D[Open knowledge gaps tile]
  C --> E[Knowledge gaps list<br/>GET /admin/knowledge-gaps]
  D --> E
  E --> F[Open a gap, most asked first]
  F --> G{Decision}
  G -- "Document needed" --> H[Set IN_PROGRESS and add a note<br/>PATCH gap]
  H --> I[Upload a document for this department]
  I --> J[Document INDEXED]
  J --> K[Set RESOLVED]
  G -- "Out of scope" --> L[Set DISMISSED with a note]
  K --> M[Employees now get an answer with citations]
```

## Who sees what

| Screen | EMPLOYEE | MANAGER | ADMIN |
|---|---|---|---|
| Chat, Search, Documents (own allowed departments) | yes | yes | yes |
| Upload, new version | no | own department | any department |
| Analytics, Knowledge gaps, Audit log, Users | no | no | yes |

The UI hides what a user cannot use, based on `permissions` from `GET /users/me`. The server enforces everything and answers 404 (not 403) for documents in other departments.
