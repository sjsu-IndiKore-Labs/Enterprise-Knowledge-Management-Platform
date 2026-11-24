---
from: manali
to: vedanti
status: open
priority: normal
created: 2026-10-19
related: manali_devops/docker/python-service.Dockerfile, contracts/ports-and-services.md
---

# OK to run the ingestion worker as its own container from the same image?

## Context
In docker-compose and ECS I run **two containers from one image**: `ai-service` (`uvicorn app.main:app`) and
`ai-worker` (`python -m app.ingestion.worker`). This way ingestion scales separately and a slow 60-page PDF
never blocks chat answers.

## Question
1. Is `python -m app.ingestion.worker` the right entrypoint (module `vedanti_ai/app/ingestion/worker.py`)?
2. Dependencies via `pyproject.toml` at `vedanti_ai/` root (`pip install .`), package named `app`?
3. Postgres connection honours the new `POSTGRES_SSLMODE` env var (`disable` locally, `require` in AWS)?
4. The worker must handle SIGTERM (ECS sends it on deploy): stop polling, finish/abandon the current message, exit within 30 s.
5. Uvicorn runs with `UVICORN_WORKERS=2`. Any reason to keep state in memory that would break with 2 workers (e.g. the embedding cache is per-process, which is fine)?

## Proposed answer
Yes to 1–4; 2 workers are fine.

## Answer
