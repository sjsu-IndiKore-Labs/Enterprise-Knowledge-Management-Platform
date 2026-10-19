# vedanti_ai/ — AI / RAG pipeline (owner: Vedanti)

`ai-service`: Python 3.11 + FastAPI (see `docs/adr/0002-python-fastapi-for-ai-service.md`). One container runs both
the HTTP API (search/answer) and the SQS ingestion worker (separate process or background task).

```
vedanti_ai/
  pyproject.toml                 # deps: fastapi, uvicorn, pydantic v2, psycopg[binary,pool], pgvector, boto3,
                                 #       openai, tiktoken, pypdf / pdfplumber, python-docx, sse-starlette, structlog
  app/
    main.py                      # FastAPI app, /health, X-Internal-Token dependency, error handler
    config.py                    # pydantic-settings, env names exactly from .env.example
    api/search.py  api/answer.py
    ingestion/worker.py          # SQS long-poll loop -> pipeline -> status events
    ingestion/extract_pdf.py  ingestion/extract_docx.py  ingestion/chunker.py
    rag/embeddings.py            # OpenAI + FakeEmbeddings (AI_PROVIDER=fake) + cache
    rag/retriever.py             # hybrid SQL with mandatory department pre-filter
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
make up && make seed                                  # repo root
cd vedanti_ai && python -m venv .venv && . .venv/bin/activate && pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000             # API
python -m app.ingestion.worker                        # SQS worker
AI_PROVIDER=fake uvicorn app.main:app --port 8000     # no OpenAI key needed
```
