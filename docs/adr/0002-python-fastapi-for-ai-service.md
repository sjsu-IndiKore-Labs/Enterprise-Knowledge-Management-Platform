# ADR-0002: Python + FastAPI for ai-service; Spring Boot for business services

Status: Proposed (2026-10-19) — awaiting acknowledgement in `questions/to-all/`

## Context
The project spec mandates Spring Boot microservices for the backend but does not fix the language of the RAG pipeline.
The best tooling for PDF/DOCX extraction, tokenization (tiktoken), OpenAI SDK, and RAG evaluation (RAGAS) is in Python.

## Decision
- `ai-service` (Vedanti): Python 3.11, FastAPI, psycopg 3 + pgvector, boto3, openai, sse-starlette.
- `core-api`, `audit-service` (Ayushi): Java 21, Spring Boot 3.
- `web` (Changhyun): Next.js + TypeScript.
They communicate only through `contracts/` (REST/SSE + SQS + shared DB tables with clear ownership).

## Consequences
Two backend languages → contracts and camelCase/naming conventions must be followed strictly. Spring Boot still owns
every business endpoint, auth and RBAC, satisfying the spec. If the team prefers all-Java, Spring AI can replace
FastAPI with the same contracts.
