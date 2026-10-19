# manali_devops/ — DevOps & Cloud (owner: Manali, team lead)

```
manali_devops/
  localstack/init-aws.sh         # creates S3 bucket + SQS queues (+ DLQs) when LocalStack is ready
  jenkins/Jenkinsfile            # Jenkins pipeline (build, test, push to ECR, deploy to ECS)
  infra/terraform/               # AWS: VPC, ECR, ECS Fargate, ALB, RDS Postgres+pgvector, S3 (KMS), SQS, CloudWatch, Secrets Manager
  load-test/chat.js              # k6 load test using resources/load-test/queries.csv
  scripts/                       # helper scripts (seed, wait-for, ecs deploy)
```

Root files also owned by Manali: `docker-compose.yml`, `Makefile`, `.env.example`, `.github/workflows/*`.

## Environments
| Env | Where | Deployed by | Data |
|---|---|---|---|
| local | docker-compose + LocalStack | `make up-apps` | dev seed + sample corpus |
| staging | AWS ECS Fargate (us-west-2) | GitHub Actions on merge to `main` (+ Jenkins pipeline for the course requirement) | dev seed + sample corpus |
