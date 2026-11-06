# Cross-platform local validation

Run these commands from macOS, Linux, or Windows WSL2. Docker Desktop must be running.

```bash
make validate-local
make up
docker compose ps
make up-apps
docker compose ps
```

Expected infrastructure:

- `postgres` is healthy on port `5432`.
- `localstack` is healthy on port `4566`.
- LocalStack contains the S3 bucket and three primary SQS queues plus three DLQs.

When application images are available, verify:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8081/actuator/health
curl --fail http://localhost:8000/health
curl --fail http://localhost:3000/api/health
```

Windows users should run the commands from WSL2 with Docker Desktop's WSL integration
enabled. Do not commit `.env`, Terraform state, cloud credentials, or generated build
directories.
