# Common commands. Owner: Manali.  Run `make help`.
SHELL := /bin/bash
COMPOSE := docker compose
API ?= http://localhost:8080
PY ?= .venv/bin/python
TF_DIR := manali_devops/infra/terraform
TF_ENV ?= staging
TAG ?= $(shell git rev-parse HEAD)
AJV := npx --yes -p ajv-cli@5 -p ajv-formats@3 ajv validate --spec=draft2020 --strict=false -c ajv-formats

.PHONY: help env validate-local validate-localstack up up-apps down reset logs ps seed migrate-raw psql mock-core-api mock-ai-service mocks lint-contracts \
        corpus ingest-corpus questions send-test-ingest test-devops test-integration test-docker smoke \
        tf-init tf-fmt tf-validate tf-test tf-plan tf-apply deploy aws-sleep aws-wake

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

env: ## Create .env from .env.example if missing
	@test -f .env || (cp .env.example .env && echo "created .env — fill OPENAI_API_KEY or set AI_PROVIDER=fake")

validate-local: ## Validate Docker Compose and required local integration files
	@bash manali_devops/scripts/validate-local.sh

validate-localstack: ## Validate LocalStack S3, CORS, SQS queues, DLQs, and policies
	$(COMPOSE) exec -T localstack bash -s < manali_devops/localstack/validate-aws.sh

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

migrate-raw: ## Contract schema via psql, for infra tests only (core-api Flyway can't migrate it; use make reset)
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

mocks: ## Prism mocks in Docker: core-api :4010, ai-service :4011 (no Node needed)
	$(COMPOSE) --profile mocks up -d mock-core-api mock-ai-service

# ---------------------------------------------------------------- DevOps tests
.venv-devops:
	python3 -m venv .venv-devops && .venv-devops/bin/pip install -q -r manali_devops/tests/requirements.txt

test-devops: .venv-devops ## Config tests: env vars, compose, Dockerfiles, workflows, scripts (no Docker needed)
	.venv-devops/bin/pytest manali_devops/tests -m "not integration"

test-integration: .venv-devops ## Live tests against local Postgres+pgvector and LocalStack (run `make up migrate-raw seed` first)
	.venv-devops/bin/pytest manali_devops/tests -m integration

test-docker: ## Build each Dockerfile template against its fixture app, run it, wait for healthy
	manali_devops/tests/docker/test_images.sh

smoke: .venv-devops ## Smoke-test a deployment: make smoke URL=http://<alb-dns>
	.venv-devops/bin/python manali_devops/tests/smoke/smoke_test.py --base-url $(URL) --allow-missing

# ---------------------------------------------------------------- Terraform (AWS)
tf-init: ## terraform init with environments/$(TF_ENV).backend.hcl
	cd $(TF_DIR) && terraform init -backend-config=environments/$(TF_ENV).backend.hcl

tf-fmt: ## terraform fmt (recursive)
	cd $(TF_DIR) && terraform fmt -recursive

tf-validate: ## terraform validate without a backend
	cd $(TF_DIR) && terraform init -backend=false -input=false >/dev/null && terraform validate

tf-test: ## terraform test (offline, mocked AWS)
	cd $(TF_DIR) && terraform init -backend=false -input=false >/dev/null && terraform test

tf-plan: ## terraform plan for $(TF_ENV)
	cd $(TF_DIR) && terraform plan -var-file=environments/$(TF_ENV).tfvars -out=$(TF_ENV).tfplan

tf-apply: ## apply the saved plan for $(TF_ENV)
	cd $(TF_DIR) && terraform apply $(TF_ENV).tfplan

deploy: ## Build, push and deploy TAG (default HEAD) to $(TF_ENV) from your machine (needs AWS creds)
	$(eval REGISTRY := $(shell aws sts get-caller-identity --query Account --output text).dkr.ecr.$(or $(AWS_REGION),us-west-2).amazonaws.com)
	aws ecr get-login-password | docker login --username AWS --password-stdin $(REGISTRY)
	ECR_REGISTRY=$(REGISTRY) manali_devops/scripts/build-push.sh $(TF_ENV) $(TAG) | tee /tmp/ekm-build.log
	manali_devops/scripts/ecs-deploy.sh $(TF_ENV) $(TAG) $$(grep '^PUSHED:' /tmp/ekm-build.log | cut -d' ' -f2- | sed 's/ai-service/ai-service ai-worker/')

aws-sleep: ## Scale staging ECS to 0 and stop RDS (save money when idle)
	manali_devops/scripts/staging-power.sh down $(TF_ENV)

aws-wake: ## Start RDS and scale staging ECS back to 1
	manali_devops/scripts/staging-power.sh up $(TF_ENV)
