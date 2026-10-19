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
