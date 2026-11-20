# vedanti_ai/ — AI / RAG pipeline (owner: Vedanti)

`ai-service`: Python 3.11 + FastAPI (see `docs/adr/0002-python-fastapi-for-ai-service.md`). One container runs the
HTTP API; the SQS ingestion worker can run as a separate process using the same image. The implementation is
contract-first and defaults to deterministic fake providers for local development.

```
vedanti_ai/
  pyproject.toml                 # deps: fastapi, uvicorn, pydantic v2, psycopg[binary,pool], pgvector, boto3,
                                 #       openai, tiktoken, pypdf / pdfplumber, python-docx, sse-starlette, structlog
  app/
    main.py                      # FastAPI app, /health, X-Internal-Token dependency, error handler
    config.py                    # pydantic-settings, env names exactly from .env.example
    api/routes.py                 # search, answer, and SSE answer routes
    ingestion/                    # SQS worker, S3 pipeline, extraction, chunking
    rag/                          # embeddings, retrieval, prompts, generation, guardrails
    rag/prompt.py  rag/generator.py  rag/guardrails.py
    db.py  models.py             # Pydantic models with camelCase aliases (contracts/conventions.md)
  eval/
    run_eval.py                  # runs resources/eval/golden_qa.jsonl, writes resources/eval/results/
    benchmarks/                  # chunking / embedding benchmark notebooks + results (Milestone 1)
  tests/
  Dockerfile
```

## Run
```bash
make up                                               # repo root: Postgres + LocalStack
cd vedanti_ai
python -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
AI_PROVIDER=fake uvicorn app.main:app --reload --port 8000
python -m app.ingestion.worker                        # separate terminal/process
pytest -q
```

The internal API requires `X-Internal-Token` for search and answer routes. The browser must call
core-api; core-api computes `allowedDepartments` from the JWT and forwards it to ai-service. The
AI service applies that list before ranking. `resources/corpus/` remains the repeatable test/demo
fixture; runtime uploads are stored in S3/LocalStack.
