# Common commands. Owner: Manali.  Run `make help`.
SHELL := /bin/bash
COMPOSE := docker compose
API ?= http://localhost:8080
PY ?= .venv/bin/python
AJV := npx --yes -p ajv-cli@5 -p ajv-formats@3 ajv validate --spec=draft2020 --strict=false -c ajv-formats

.PHONY: help env up up-apps down reset logs ps seed psql mock-core-api mock-ai-service lint-contracts \
        corpus ingest-corpus questions send-test-ingest

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

env: ## Create .env from .env.example if missing
	@test -f .env || (cp .env.example .env && echo "created .env — fill OPENAI_API_KEY or set AI_PROVIDER=fake")

up: env ## Start postgres+pgvector and localstack (S3, SQS)
	$(COMPOSE) up -d postgres localstack
	@echo "postgres :5432  localstack :4566"

up-apps: env ## Build and start infra + all application services that have a Dockerfile
	$(COMPOSE) --profile apps up -d --build

down: ## Stop everything (keeps data)
	$(COMPOSE) --profile apps down

reset: ## Stop everything and DELETE local data volumes
	$(COMPOSE) --profile apps down -v

logs: ## Tail logs (make logs s=core-api)
	$(COMPOSE) logs -f $(s)

ps: ## Show running services
	$(COMPOSE) ps

seed: ## Load dev users (requires tables: run core-api once, or Flyway)
	$(COMPOSE) exec -T postgres psql -U $${POSTGRES_USER:-ekm} -d $${POSTGRES_DB:-ekm} < contracts/database/seed/dev_seed.sql

migrate-raw: ## Apply V*.sql migrations directly with psql (only before core-api/Flyway exists!)
	@for f in contracts/database/migrations/V*.sql; do echo "applying $$f"; \
	  $(COMPOSE) exec -T postgres psql -v ON_ERROR_STOP=1 -U $${POSTGRES_USER:-ekm} -d $${POSTGRES_DB:-ekm} < $$f; done

psql: ## Open psql in the postgres container
	$(COMPOSE) exec postgres psql -U $${POSTGRES_USER:-ekm} -d $${POSTGRES_DB:-ekm}

mock-core-api: ## Prism mock of core-api on :4010 (frontend can work without backend)
	npx --yes @stoplight/prism-cli@5 mock -p 4010 --cors contracts/openapi/core-api.yaml

mock-ai-service: ## Prism mock of ai-service on :4011 (backend can work without AI)
	npx --yes @stoplight/prism-cli@5 mock -p 4011 contracts/openapi/ai-service.yaml

lint-contracts: ## Lint OpenAPI specs and validate event examples against their JSON Schemas
	npx --yes @redocly/cli@1 lint \
	  contracts/openapi/core-api.yaml contracts/openapi/ai-service.yaml
	$(AJV) -s contracts/events/document-ingest-requested.schema.json -d contracts/events/examples/document-ingest-requested.json
	$(AJV) -s contracts/events/document-ingest-status.schema.json -d "contracts/events/examples/document-ingest-status-*.json"
	$(AJV) -s contracts/events/audit-event.schema.json -d "contracts/events/examples/audit-event-*.json"

.venv:
	python3 -m venv .venv && .venv/bin/pip install -q -r resources/scripts/requirements.txt

corpus: .venv ## Re-render resources/corpus/files (PDF/DOCX) and edge cases from Markdown
	$(PY) resources/scripts/build_corpus.py

ingest-corpus: .venv ## Upload the sample corpus through core-api (API=http://... to target staging)
	$(PY) resources/scripts/ingest_corpus.py --api $(API)

send-test-ingest: ## Put the example ingest message on the local SQS ingest queue
	aws --endpoint-url http://localhost:4566 --region us-west-2 sqs send-message \
	  --queue-url http://localhost:4566/000000000000/ekm-document-ingest \
	  --message-body file://contracts/events/examples/document-ingest-requested.json

questions: ## List open questions per inbox
	@for d in questions/to-*; do \
	  open=$$(grep -l "^status: open" $$d/*.md 2>/dev/null); \
	  if [ -n "$$open" ]; then echo "== $$d"; echo "$$open" | sed 's/^/   /'; fi; done; true
