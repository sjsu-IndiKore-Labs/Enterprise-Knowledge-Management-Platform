---
from: manali
to: all
status: open
priority: blocking
created: 2026-10-19
related: contracts/README.md, docs/adr/
---

# Please confirm architecture + contracts v1 before writing service code

## Context
The repo structure, contracts and TODOs were set up on 2026-10-19. Milestone 1 (contracts, wireframes,
baseline benchmark, Docker templates) is overdue, and Milestone 2 (ingestion + upload + S3/SQS) ends Oct 25.
Everything in `contracts/` is a **proposal** until each of us acknowledges it here.

## Question
Do you agree with these decisions (details in `docs/adr/`)?
1. **ADR-0002**: ai-service is **Python 3.11 + FastAPI** (Vedanti), backend is **Java 21 + Spring Boot 3** (Ayushi), frontend **Next.js + TypeScript** (Changhyun).
2. **ADR-0003**: contracts-first — `contracts/` is the source of truth; camelCase JSON; error body; ports 3000/8080/8081/8000.
3. **ADR-0004**: ai-service writes `document_chunks` directly; core-api owns every other business table; Flyway migrations live in `contracts/database/migrations` and are applied only by core-api.
4. **ADR-0005**: browser talks only to core-api; chat streaming via SSE relayed by core-api.
5. Branching: `<member>/<topic>` → PR into `develop` → `main` at each milestone.

## Proposed answer
Yes to all. Raise objections as a separate question file in `to-all/` within 2 days (by 2026-10-21).

## Answer

## Acknowledgements
- Manali 2026-10-19 ✔
