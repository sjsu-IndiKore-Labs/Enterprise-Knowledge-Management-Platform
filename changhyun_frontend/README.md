# changhyun_frontend/ — Frontend & UX (owner: Changhyun Kim)

Next.js (App Router) + TypeScript + Tailwind CSS. Package manager: **pnpm** (or npm — pick one and commit the lockfile).

```
changhyun_frontend/
  package.json, next.config.mjs, tsconfig.json, tailwind.config.ts
  src/
    app/
      (auth)/login/page.tsx
      (portal)/chat/page.tsx            # chat + streaming + citations
      (portal)/chat/[conversationId]/page.tsx
      (portal)/documents/page.tsx       # library, filters, status badges
      (portal)/documents/upload/page.tsx
      (portal)/documents/[id]/page.tsx  # metadata, versions, preview
      (portal)/search/page.tsx          # hybrid search results (no LLM)
      (admin)/admin/analytics/page.tsx
      (admin)/admin/knowledge-gaps/page.tsx
      (admin)/admin/users/page.tsx
      api/health/route.ts
    lib/api/schema.d.ts                 # GENERATED from contracts/openapi/core-api.yaml — never hand-edit
    lib/api/client.ts                   # typed fetch wrapper (openapi-fetch), auth header, error body handling
    lib/api/chatStream.ts               # fetch + ReadableStream SSE parser (contracts/streaming.md)
    components/  hooks/  styles/
  wireframes/                           # Milestone 1 wireframes (PNG/Figma links)
  Dockerfile
```

## Run
```bash
pnpm install
pnpm gen:api                 # openapi-typescript ../contracts/openapi/core-api.yaml -o src/lib/api/schema.d.ts
make mock-core-api           # (repo root) Prism mock on :4010 — develop without the backend
NEXT_PUBLIC_API_BASE_URL=http://localhost:4010 pnpm dev
```

## Developing against the mock server

`pnpm mock` starts a small stateful stand-in for core-api on <http://localhost:4010> (same port as the Prism mock; `.env.example` already points there). Unlike Prism it keeps state, so the screens that depend on behavior can be checked:

- upload goes `QUEUED` → `PROCESSING` (after ~4 s) → `INDEXED` (after ~9 s); a file whose name contains `fail` ends `FAILED` with an error message
- roles from the seed users (all use password `Password123!`): `harper.hrmgr@helios.example.com` (MANAGER, HR), `hannah.hr@…` (EMPLOYEE, HR), `alice.admin@…` (ADMIN), `ethan.engmgr@…` (MANAGER, ENGINEERING)
- other departments' documents return 404, EMPLOYEE upload returns 403, a MANAGER uploading to another department returns 403
- files over 25 MB return 413, other file types 415, missing fields 400, a search containing `trigger502` returns 502
- `curl -X POST localhost:4010/__mock/expire-tokens` makes the next call return 401 (session-expiry check); `POST /__mock/reset` restores the seed data
- search and citation preview use the real sample corpus in `resources/corpus` when it is next to the repo (otherwise four built-in documents)

`pnpm mock:test` checks the mock itself (20 checks, no dependencies). The mock follows the contract, not Ayushi's current controllers, so it is a development aid and not proof that the real backend works.

### Manual check of M2 and M3 (about 10 minutes)

Run `pnpm mock` in one terminal and `pnpm dev` in another, then open <http://localhost:3000>.

1. **Login:** wrong password shows "Invalid email or password". Sign in as `harper.hrmgr@helios.example.com`. Name, role and department show in the top bar. Reload the page: you stay signed in.
2. **Role rules:** as `hannah.hr@…` (EMPLOYEE) there is no Upload link and `/documents/upload` explains who can upload. As Harper (MANAGER) the department is fixed to Human Resources. As `alice.admin@…` any department can be chosen.
3. **Upload validation:** a `.txt` file, an empty file and a file over 25 MB each show a message and are not accepted. Dropping two files says "Upload one file at a time".
4. **Upload and status:** upload a PDF. The progress bar fills, then the tracker steps Uploaded → Queued → Processing → Indexed without a reload. Upload a file named `will-fail.pdf`: the tracker ends on Failed with the reason.
5. **Library:** more than one page of documents for ADMIN, filters (department, category, status, text) and paging work. A just-uploaded document updates its badge in the list on its own. A failed one shows its tooltip.
6. **Detail:** versions list, "Upload new version" adds version 2 and it processes. Download opens a PDF. Opening a document from another department's URL shows "Document not found".
7. **Session expiry:** run the `expire-tokens` command above, then click anything. You land on `/login` with "Your session expired", and after signing in you return to the page you were on.
8. **Search:** "how many vacation days do I get?" ranks the PTO policy first. Mode `Keyword` with "vacation" finds nothing; `Semantic` finds the PTO policy. Matches are highlighted. A search for `trigger502` shows the "temporarily unavailable" message.
9. **Citation preview:** "Preview passage" opens the panel with the full passage and page; Esc closes it; "Open at page N" opens the PDF at that page.
