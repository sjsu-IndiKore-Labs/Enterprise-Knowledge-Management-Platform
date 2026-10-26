# DevOps Requirement Freeze

**Enterprise Knowledge Management Platform**

| | |
|---|---|
| Project | Enterprise Knowledge Management Platform |
| Scope owner | Manali — DevOps and Cloud |
| Freeze date | October 26, 2026 |
| Target demonstration | December 2, 2026 |

This document defines the DevOps and Cloud MVP scope for the December 2, 2026 demonstration.

## Objective

Provide a repeatable path for the team services to run locally, pass automated validation, and deploy to one AWS staging environment. The service owners remain responsible for application business logic; the DevOps scope connects, packages, deploys, and monitors their services.

## In Scope

- Docker support for the web frontend, core backend API, audit service, and AI/RAG service.
- Docker Compose for local development, including local PostgreSQL with pgvector and LocalStack for S3 and SQS.
- GitHub Actions for pull-request validation, application tests, and Docker image builds.
- One basic Jenkins pipeline for the course requirement.
- Private ECR repositories for versioned Docker images.
- One ECS/Fargate staging environment in us-west-2.
- Application Load Balancer for public web and API access.
- Private S3 storage with encryption and versioning.
- SQS queues with dead-letter queues.
- RDS PostgreSQL with pgvector, coordinated with the backend owner.
- CloudWatch logs and basic operational alarms.
- Secrets managed through AWS Secrets Manager or Parameter Store.
- Deployment, rollback, and troubleshooting documentation.

DevOps will provide short operational SOPs for local startup, CI/CD failures, ECS deployment and rollback, health monitoring, SQS dead-letter handling, and AWS cost cleanup.

## Out of Scope

- Production deployment, 24/7 production support, formal SLAs, and on-call incident response.
- Multiple AWS regions or accounts.
- Kubernetes.
- Blue-green or canary deployments.
- Advanced autoscaling.
- Multi-tenant infrastructure.
- Cross-region disaster recovery.
- Full distributed tracing.
- Backend, frontend, authentication, or RAG business logic.
- Additional document formats beyond the agreed project scope.

## Acceptance Criteria

- [ ] Available services can run through Docker Compose.
- [ ] Pull requests trigger automated validation.
- [ ] Docker images can be built and stored in private ECR.
- [ ] ECS/Fargate can run the staging containers.
- [ ] Health checks and CloudWatch logs work.
- [ ] S3 and SQS integrations are available to application services.
- [ ] A failed or previous deployment can be rolled back.
- [ ] The complete process is documented for the final demonstration.

## Dependencies From Service Owners

Each service owner must provide the following before their service can be integrated:

| Required input | Purpose |
|---|---|
| Source code | Application implementation to build and test |
| Dockerfile | Container packaging instructions |
| Port and health endpoint | Routing and ECS health checks |
| Startup and test commands | Local execution and CI validation |
| Environment variables | Runtime configuration without hard-coded secrets |
| External dependencies | Database, queue, storage, or API integration planning |

## Freeze Rule

New infrastructure features, deployment strategies, cloud services, or environments require team agreement and must not delay the agreed MVP. Any proposed addition must identify its value, owner, time cost, and impact on the December 2 demonstration.

## Decision

This scope is the baseline for DevOps implementation and team coordination. The service owners may continue to develop their application features, but their integration requirements must remain compatible with this freeze.
