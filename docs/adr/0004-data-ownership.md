# ADR-0004: Single database, explicit table ownership

Status: Proposed (2026-10-19)

## Decision
One PostgreSQL 16 + pgvector database. core-api applies all Flyway migrations from `contracts/database/migrations`.
ai-service writes only `document_chunks`; audit-service writes only `audit_logs`, `query_logs`, `knowledge_gaps`; core-api writes the rest.
`document_chunks` denormalizes `department_code`, `document_title`, `category` so retrieval with the department pre-filter is a single-table query.

## Consequences
Simple ops for a semester project (one RDS instance). Services are coupled at the schema level, mitigated by ownership rules and immutable migrations.
