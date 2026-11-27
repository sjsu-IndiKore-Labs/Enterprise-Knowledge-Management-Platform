# ADR-0006: Deploy on ECS Fargate with Terraform, shared Dockerfile templates, OIDC-based CD

Status: Proposed (2026-10-19)

## Decision
- Infrastructure as code with **Terraform** (`manali_devops/infra/terraform`), one staging environment, remote state in S3.
- **ECS Fargate** runs 5 services: web, core-api, audit-service, ai-service, **ai-worker** (same image as ai-service,
  runs `python -m app.ingestion.worker`) so ingestion scales independently of query traffic.
- **One ALB**: `/api/v1/*` → core-api, everything else → web. The web image is built with `NEXT_PUBLIC_API_BASE_URL=""`
  (same origin, no CORS). ai-service is reachable only via ECS Service Connect at `http://ai-service:8000`.
- **Dockerfile templates** owned by DevOps (`manali_devops/docker/`); members don't write Dockerfiles, they follow the
  build conventions in `manali_devops/docs/local-development.md`.
- **CD via GitHub Actions with OIDC** (no AWS keys in GitHub); images tagged with the commit SHA; Jenkins pipeline mirrors it.
- RDS enforces TLS → new env var `POSTGRES_SSLMODE` (`disable` locally, `require` in AWS).

## Consequences
~$145/month if left running 24/7 (use `make aws-sleep`). Tasks run in public subnets by default to avoid NAT costs;
security groups keep them unreachable except via the ALB. Switching to private subnets is one variable (`use_nat_gateway`).
