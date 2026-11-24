# EKM service ownership directory

**Document type:** service catalog and ownership record  
**Environment:** project-wide; deployment details refer to staging  
**Status:** demo data derived from `AGENTS.md` and repository contracts  
**Last verified:** 2026-11-23

## Ownership summary

| Service or capability | Primary owner | Technical responsibility | Repository location |
|---|---|---|---|
| `web` / web portal / frontend | Changhyun Kim | Next.js portal, UX, browser integration | `changhyun_frontend/` |
| `core-api` / knowledge API | Ayushi | Authentication, authorization, documents, search/chat proxy, backend security | `ayushi_backend/core-api/` |
| `audit-service` / audit API | Ayushi | Audit events, query logs, and knowledge-gap records | `ayushi_backend/audit-service/` |
| `ai-service` / RAG API | Vedanti | Search, answer synthesis, embeddings, citations, guardrails | `vedanti_ai/` |
| `ai-worker` / ingestion worker | Vedanti | SQS ingestion, document extraction, chunking, embedding, indexing | `vedanti_ai/` |
| AWS infrastructure / platform | Manali Mankad | Docker, Terraform, ECS, ECR, S3, SQS, RDS, CI/CD, monitoring | `manali_devops/` |
| Database schema and migrations | Ayushi with Manali infrastructure support | PostgreSQL/pgvector schema and migration lifecycle | `contracts/database/`, `ayushi_backend/` |

## Escalation guidance

- UI behavior, browser errors, and portal layout: contact the `web` owner.
- Login, permissions, document metadata, upload coordination, or public API behavior: contact the `core-api` owner.
- Audit records, audit queue processing, or knowledge-gap records: contact the `audit-service` owner.
- Retrieval quality, embeddings, answer generation, citations, or ingestion failures: contact the `ai-service` or `ai-worker` owner.
- ECS task failures, S3/SQS/IAM/RDS/ALB, deployment, or CloudWatch issues: contact the infrastructure owner.

## Scope and limitations

This is project demo ownership data, not a college-wide employee directory. It does not contain personal phone numbers, passwords, API keys, or private contact information. Add a team alias or approved support channel only after the team confirms it.

## Source references

- `../../../AGENTS.md`
- `../../../contracts/ports-and-services.md`
- `../../README.md`
