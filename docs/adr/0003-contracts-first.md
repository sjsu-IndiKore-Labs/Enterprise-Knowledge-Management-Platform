# ADR-0003: Contracts-first development

Status: Proposed (2026-10-19)

## Context
Four people with four different AI coding agents build services in parallel. Agents happily invent field names,
ports and env vars, which causes late integration failures.

## Decision
All cross-service interfaces live in `contracts/` (OpenAPI 3, JSON Schema for SQS, Flyway SQL, Markdown for auth/SSE/conventions)
and are changed only via `[contracts]` PRs approved by consumers. Frontend types are generated from OpenAPI. Mocks (Prism, `AI_PROVIDER=fake`)
let everyone work before dependencies are ready. CI lints the contracts and validates examples.

## Consequences
Slightly slower to change an interface; much faster integration. `contracts/CHANGELOG.md` doubles as documentation for the report.
