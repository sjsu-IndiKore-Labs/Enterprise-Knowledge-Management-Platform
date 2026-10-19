# Enterprise Knowledge Management Platform

Secure internal knowledge portal powered by **Retrieval-Augmented Generation (RAG)** — SJSU Enterprise Software, Fall 2026.
Upload PDF/DOCX company documents, ask questions in natural language, get grounded answers with page-level citations,
restricted to the departments you are allowed to see. Final demo: **December 2, 2026**.

| Member | Role | Folder | Plan |
|---|---|---|---|
| Ayushi | Backend & Security (Spring Boot, PostgreSQL/pgvector, JWT, RBAC, SQS, audit) | [`ayushi_backend/`](ayushi_backend/) | [TODO](ayushi_backend/TODO.md) |
| Changhyun Kim | Frontend & UX (Next.js, upload, streaming chat, citations, admin dashboard) | [`changhyun_frontend/`](changhyun_frontend/) | [TODO](changhyun_frontend/TODO.md) |
| Vedanti | AI / RAG (parsing, chunking, embeddings, hybrid search, prompts, evaluation) | [`vedanti_ai/`](vedanti_ai/) | [TODO](vedanti_ai/TODO.md) |
| Manali (lead) | DevOps & Cloud (Docker, CI/CD, AWS ECS/S3/SQS/RDS, CloudWatch) | [`manali_devops/`](manali_devops/) | [TODO](manali_devops/TODO.md) |

## Start here
- **Using an AI coding agent?** It must read [`AGENTS.md`](AGENTS.md) (Claude Code, Cursor, Copilot, Gemini and Codex are pointed there automatically).
- **Interfaces between services**: [`contracts/`](contracts/) — the source of truth.
- **Questions for another member's agent**: [`questions/`](questions/).
- **Architecture & flows**: [`docs/architecture.md`](docs/architecture.md), [`docs/integration-guide.md`](docs/integration-guide.md), [`docs/timeline.md`](docs/timeline.md).
- **Test data**: [`resources/`](resources/) — fictional company corpus (PDF/DOCX), seed users, 40-question RAG eval set.

## Quick start (local)
Requirements: Docker Desktop, Node 20 (for `npx` mocks/linters), Python 3.11, Java 21 (backend only).
```bash
make env            # creates .env from .env.example
make up             # postgres + pgvector, LocalStack (S3 + SQS)
make migrate-raw    # only until core-api (Flyway) exists
make seed           # dev users — password in resources/seed/README.md
make mock-core-api  # mocked REST API on :4010 for frontend work
make up-apps        # all services that already have a Dockerfile
make help           # everything else
```

## Tech stack
Spring Boot 3 (Java 21) · Next.js + TypeScript · Python FastAPI · PostgreSQL 16 + pgvector · OpenAI (gpt-4o-mini, text-embedding-3-small) ·
Amazon S3 / SQS / ECS Fargate / RDS / CloudWatch · Docker · GitHub Actions · Jenkins
