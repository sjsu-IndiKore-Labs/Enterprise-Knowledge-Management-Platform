---
from: manali
to: all
status: open
priority: blocking
created: 2026-11-27
related: manali_devops/docs/local-development.md, manali_devops/tests/smoke/demo_check.py, questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md
---

# Staging is live with current code, and everyone must reset their local DB once

## Context
**Staging** (runs the code in PR #43 = current `develop` + infra fixes, deployed 2026-11-27):
`http://ekm-staging-800774594.us-west-2.elb.amazonaws.com`
- Web, login, `/users/me` and departments work through the ALB. Seed users and password are the same as local (`resources/seed/users.json`).
- Chat returns 502 until the real OpenAI key is in Secrets Manager (Manali, today).
- Uploads won't reach `INDEXED` until Ayushi's schema/consumer fixes land (see her question below).
- CD is configured: every push to `main` builds, deploys to staging and smoke-tests it.

**Local**: pull `develop` (PR #43 merged), then **once**:
```bash
make reset          # deletes your local DB + LocalStack data
make up-apps        # core-api's Flyway creates the schema and dev users. Don't run make migrate-raw any more
```
A DB built with `make migrate-raw` makes core-api (Flyway V2) and audit-service crash on startup.
No OpenAI key locally? Put `AI_PROVIDER=fake` in `.env`.

**Check your work end to end**: `python manali_devops/tests/smoke/demo_check.py --api http://localhost:8080`
(or `--api <staging URL>`). It walks `docs/demo-script.md`: upload → INDEXED, DOCX, search/chat/stream, versioning, RBAC, admin.
Today it fails only on Ayushi's items 1–3.

## What each of you needs to do
- **Ayushi (blocking demo):** fix the 5 core-api issues in
  `questions/to-ayushi/2026-11-27_from-manali_flyway-vs-contract-schema.md`: `document_chunks.embedding`/`content_tsv`
  missing in Flyway, INDEXED events lost (`@Transactional` self-invocation), DOCX 500 (`file_type` 64 chars), `OPERATIONS`
  department breaks admin chat, HTTP/1.1 in `AiServiceClient`. Tell me when they're merged and I'll redeploy staging.
- **Vedanti:** nothing to change. Your `POSTGRES_*` fix is deployed and ai-service/ai-worker run on staging. Please fill the
  Answer in `questions/to-vedanti/2026-11-27_from-manali_postgres-env-var-names.md`, acknowledge
  `questions/to-all/2026-10-19_from-manali_confirm-architecture-and-contracts-v1.md`, and tick your TODO.
- **Changhyun:** admin endpoints (analytics summary/top-queries, knowledge-gaps, audit-logs, users) all return 200 locally,
  so the dashboard/gaps/audit/users pages can be built against them tonight. Your web image builds and is deployed.
  `questions/to-changhyun/2026-10-19_from-manali_nextjs-container-requirements.md` is already satisfied
  (`output: 'standalone'`, `/api/health`). Just mark it answered.

## Question
Reply below with ✔ once you've reset locally and read your part.

## Proposed answer
Everyone resets tonight. Ayushi's fixes are merged by Nov 29, so staging can be loaded with the corpus and rehearsed Nov 30.

## Answer
<!-- Filled by the recipient: name, date, decision, what changes (contract? TODO?) -->

## Acknowledgements
- Manali 2026-11-27 ✔
