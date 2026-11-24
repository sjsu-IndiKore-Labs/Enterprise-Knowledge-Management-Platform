# EKM service knowledge pack

**Purpose:** grounding documents for questions about EKM services, ownership, architecture, dependencies, deployment, and operations.

**Audience:** internal employees, developers, operators, and the EKM assistant.

**Status:** demo/staging documentation generated from the repository on 2026-11-23.

These documents describe this project. They are not generic enterprise policies and should not be used as a source of truth for another organization.

## Documents

| Document | Questions it supports |
|---|---|
| [ownership-directory.md](ownership-directory.md) | Who owns a service? Who supports it? |
| [system-architecture.md](system-architecture.md) | How do the services communicate? |
| [web-service.md](web-service.md) | What does the web portal do? |
| [core-api-service.md](core-api-service.md) | What does core-api own and depend on? |
| [audit-service.md](audit-service.md) | How are audit events processed? |
| [ai-service.md](ai-service.md) | How does search and answer generation work? |
| [ai-worker.md](ai-worker.md) | How are documents ingested and indexed? |

## Grounding rules

- Treat repository contracts as authoritative for ports, queues, event names, and API behavior.
- Treat the ownership directory as project demo data and update it when team ownership changes.
- Never put passwords, API keys, tokens, or secret values in these documents.
- Service names and aliases are intentionally repeated so retrieval can match natural-language questions.
- Every generated answer should cite the document and section used.

## Export for ingestion

The current upload flow is PDF/DOCX-oriented. These Markdown files are the canonical editable source. Export each file to PDF or DOCX before uploading it to the document portal, or extend the ingestion pipeline to accept Markdown directly.

## Repository sources

- `../../../contracts/ports-and-services.md`
- `../../../contracts/openapi/core-api.yaml`
- `../../infra/terraform/ecs.tf`
- `../../infra/terraform/iam.tf`
- `../../infra/terraform/s3.tf`
- `../../infra/terraform/sqs.tf`
- `../../README.md` and each service owner's README/TODO
