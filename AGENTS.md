# AGENTS.md — read this first (every coding agent, every session)

This file is the **single source of instructions for every AI coding agent** working in this repo
(Claude Code, Cursor, Codex, Copilot, Gemini, Windsurf, …). `CLAUDE.md`, `GEMINI.md`,
`.cursor/rules/project.mdc` and `.github/copilot-instructions.md` only point here.

## 1. What we are building

**Enterprise Knowledge Management Platform** — a secure internal knowledge portal powered by
Retrieval-Augmented Generation (RAG). Employees upload PDF/DOCX documents (SOPs, policies, manuals),
the platform parses, chunks and embeds them into PostgreSQL + pgvector, and an AI assistant answers
natural-language questions **only from documents the user is allowed to see**, with source citations
(document + page). Admins get analytics on usage and knowledge gaps (unanswered questions).

SJSU Enterprise Software, Fall 2026. Final demo: **December 2, 2026**.

## 2. Who owns what

Find out which team member you are working for (ask the user if it is not obvious from the request,
the branch name `<member>/<topic>`, or `git config user.name`). Then work **inside that member's folder**.

| Member | Role | Your folder | Your inbox | Runtime services owned |
|---|---|---|---|---|
| Ayushi | Backend & Security | [`ayushi_backend/`](ayushi_backend/) | [`questions/to-ayushi/`](questions/to-ayushi/) | `core-api` (8080), `audit-service` (8081), DB migrations |
| Changhyun Kim | Frontend & UX | [`changhyun_frontend/`](changhyun_frontend/) | [`questions/to-changhyun/`](questions/to-changhyun/) | `web` (3000) |
| Vedanti | AI / RAG pipeline | [`vedanti_ai/`](vedanti_ai/) | [`questions/to-vedanti/`](questions/to-vedanti/) | `ai-service` (8000) incl. ingestion worker |
| Manali (lead) | DevOps & Cloud | [`manali_devops/`](manali_devops/) + root infra files | [`questions/to-manali/`](questions/to-manali/) | docker-compose, CI/CD, AWS |

Every member's plan is in their `TODO.md`. **Read all four TODO.md files once** so you know what the
other agents are building and what they expect from you.

## 3. Golden rules (these exist to prevent integration problems)

1. **Contracts first.** Everything that crosses a service boundary is defined in [`contracts/`](contracts/):
   REST APIs (OpenAPI), SQS message schemas, the database schema, JWT claims, error format, ports and env vars.
   - Never invent an endpoint, field, queue, table, column, env var or port that is not in `contracts/`.
   - If you need something that is missing or wrong, **write a question** to the owner (rule 4) and
     code against your best guess behind a small adapter so it is easy to change.
   - Contract changes go in their own PR, must update [`contracts/CHANGELOG.md`](contracts/CHANGELOG.md),
     and must be approved by every member whose service consumes the changed contract.
2. **Stay in your lane.** Only edit files in your own folder. Shared files (`contracts/`, `docs/`,
   `resources/`, root files) only via a PR that says so in its title, e.g. `[contracts] add tags filter`.
   Never "fix" another member's code — open a question instead.
3. **One naming convention everywhere** (see [`contracts/conventions.md`](contracts/conventions.md)):
   JSON = `camelCase`, DB = `snake_case`, IDs = UUID strings, times = ISO-8601 UTC (`2026-10-19T17:00:00Z`),
   enums = `UPPER_SNAKE_CASE`, errors = the shared error body. Env var names come from [`.env.example`](.env.example) only.
4. **Questions mailbox.** At the **start of every session**, check `questions/to-<you>/` and
   `questions/to-all/` for `status: open` files and answer them first. To ask another member's agent
   something, follow [`questions/README.md`](questions/README.md). One question = one file.
5. **Mock, don't wait.** If a dependency is not ready, use the mock: `make mock-core-api`
   (Prism serves the OpenAPI spec), `AI_PROVIDER=fake` for the AI service, sample events in
   `contracts/events/examples/`, sample documents in `resources/`.
6. **Security is not optional.** Department filtering is decided **only** by `core-api` from the JWT;
   never trust a department sent by the browser. Never log JWTs, passwords, or OpenAI keys.
   Never commit secrets — use `.env` (git-ignored).
7. **Keep your TODO.md honest.** Tick boxes as you finish, add new items you discover, and note
   blockers with a link to the question file.
8. **Small PRs into `develop`.** Branch `<member>/<short-topic>` (e.g. `vedanti/pdf-chunker`),
   PR into `develop`, CI must pass. `develop` → `main` at each milestone. See [`docs/conventions.md`](docs/conventions.md).

## 4. Repo map

```
AGENTS.md                ← you are here
contracts/               ← source of truth for every interface (OpenAPI, events, DB, JWT, env, ports)
docs/                    ← architecture, integration flows, timeline, ADRs, conventions, demo script
questions/               ← agent-to-agent mailbox (to-ayushi, to-changhyun, to-vedanti, to-manali, to-all)
resources/               ← sample corpus (PDF/DOCX), seed users, RAG eval set, load-test queries, edge cases
ayushi_backend/          ← Spring Boot: core-api + audit-service
changhyun_frontend/      ← Next.js + TypeScript web portal
vedanti_ai/              ← Python FastAPI ai-service: ingestion worker + retrieval + answer synthesis
manali_devops/           ← Dockerfiles helpers, LocalStack init, Jenkins, AWS infra (Terraform), load tests
docker-compose.yml       ← local stack (owned by Manali)
.env.example             ← every env var name used by any service
Makefile                 ← common commands (make help)
```

## 5. Local development in one minute

```bash
cp .env.example .env          # fill OPENAI_API_KEY or set AI_PROVIDER=fake
make up                       # postgres+pgvector and localstack (S3 + SQS)
make migrate-raw              # apply DB schema directly (only until core-api/Flyway exists)
make seed                     # dev departments + users (password in resources/seed/README.md)
make mock-core-api            # frontend can develop against a mocked backend on :4010
make up-apps                  # build and run every service that exists so far
```

## 6. Before you finish a session

- Code builds and your tests pass locally.
- No changes outside your folder unless the PR is explicitly a shared-file PR.
- Your `TODO.md` is updated; open questions you asked are listed under "Blocked / waiting on".
- If you changed behaviour another service depends on, there is a contract change + CHANGELOG entry.
