# Local development with Docker Compose

| Command | Starts |
|---|---|
| `make up` | `postgres` (pgvector, :5432) + `localstack` (S3 + SQS, :4566); bucket and 6 queues are created automatically |
| `make migrate-raw seed` | applies `contracts/database/migrations` with psql + loads dev users (only until core-api/Flyway exists) |
| `make mocks` | Prism mocks in Docker: core-api :4010, ai-service :4011 |
| `make up-apps` | builds and starts **core-api, audit-service, ai-service, ai-worker, web** from the templates in `manali_devops/docker/` |
| `make logs s=core-api` / `make ps` / `make down` / `make reset` | logs / status / stop / stop and delete data |

`make up-apps` fails for services whose code doesn't exist yet. Start only what exists, e.g.
`docker compose --profile apps up -d --build ai-service ai-worker`.

## How config flows
`.env` (copied from `.env.example`) → `env_file` of every app container. Compose overrides the values that differ inside
the Docker network: `POSTGRES_HOST=postgres`, `AWS_ENDPOINT_URL=http://localstack:4566`, `AI_SERVICE_URL=http://ai-service:8000`,
`POSTGRES_SSLMODE=disable`. In AWS the same names are injected by Terraform (`ecs.tf`) and Secrets Manager.

## What each member's code must provide for its container
| Service | Must provide |
|---|---|
| core-api / audit-service | Maven multi-module project in `ayushi_backend/` (`mvn package -pl core-api -am`, same for `audit-service`); listen on `PORT` (8080/8081); `/actuator/health`; core-api runs Flyway from `classpath:db/migration`; JDBC URL uses `sslmode=${POSTGRES_SSLMODE}` |
| ai-service / ai-worker | `vedanti_ai/pyproject.toml`; `app.main:app` with `GET /health`; `python -m app.ingestion.worker` runs the SQS loop |
| web | `changhyun_frontend/package.json` with `build`; `output: 'standalone'`; `GET /api/health`; API calls use `NEXT_PUBLIC_API_BASE_URL` (empty string = same origin in AWS) |

## Troubleshooting
- **LocalStack unhealthy**: `docker compose logs localstack | grep init-aws`. The image is pinned to 3.8 because newer ones may need an auth token.
- **Port already in use**: something else is on 5432/4566/8080; stop it or change the left side of `ports:` locally (don't commit it).
- **Apple Silicon vs AWS**: local builds are arm64, CI builds linux/amd64 for Fargate. Both work.
- **Fresh database**: `make reset && make up && make migrate-raw seed`.
