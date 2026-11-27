# manali_devops/ — DevOps & Cloud (owner: Manali, team lead)

```
manali_devops/
  docker/                 # Dockerfile templates for every service (java, python, node) + .dockerignore + hadolint config
  localstack/init-aws.sh  # creates the S3 bucket + SQS queues (+ DLQs) when LocalStack starts
  infra/terraform/        # AWS: VPC, ALB, ECS Fargate, ECR, RDS+pgvector, S3 (KMS), SQS, Secrets, CloudWatch, OIDC
    bootstrap/            #   one-time remote-state bucket + lock table
    environments/         #   staging.tfvars / staging.backend.hcl (examples committed, real ones git-ignored)
    tests/                #   `terraform test` with mocked AWS (offline)
  scripts/                # build-push.sh, ecs-deploy.sh, staging-power.sh (used by Actions, Jenkins, make)
  jenkins/Jenkinsfile     # Jenkins pipeline (same stages as GitHub Actions)
  load-test/chat.js       # k6 load test
  tests/                  # pytest config + integration tests, image tests, fixtures, smoke test
  docs/                   # runbooks (below)
```
Root files also owned by Manali: `docker-compose.yml`, `Makefile`, `.env.example`, `.github/workflows/*`.

| Doc | For |
|---|---|
| [docs/local-development.md](docs/local-development.md) | Running the stack locally, what each service must provide to be containerized |
| [docs/aws-architecture.md](docs/aws-architecture.md) | AWS diagram, security summary, cost estimate |
| [docs/aws-deployment.md](docs/aws-deployment.md) | Step-by-step first deploy, GitHub setup, day-2 ops, teardown |
| [docs/ci-cd.md](docs/ci-cd.md) | Workflows, shared scripts, Jenkins setup |
| [docs/operations-runbook.md](docs/operations-runbook.md) | What to do when an alarm fires |
| [docs/testing.md](docs/testing.md) | All DevOps tests and how to run them |

## Environments
| Env | Where | Deployed by |
|---|---|---|
| local | docker-compose + LocalStack | `make up` / `make up-apps` |
| staging | AWS ECS Fargate (us-west-2) | `cd-staging.yml` on merge to `main`, Jenkins `DEPLOY=true`, or `make deploy` |
