# DevOps tests

| Layer | Command | Needs | What it proves |
|---|---|---|---|
| Config tests (47) | `make test-devops` | Python | every env var used by compose/Terraform is in `.env.example`; compose ports = contract; Dockerfiles pinned, multi-stage, non-root, correct health paths, no secrets; workflows pin actions and use OIDC; scripts are strict; `ecs-deploy.sh` swaps only the image (fake AWS CLI) |
| Terraform unit tests (8 runs) | `make tf-test` | Terraform | names match contracts; DLQ/visibility; encryption + private RDS + TLS; task definitions (env from `.env.example`, least-privilege secrets, no secret values, worker command); ALB routing; HTTPS and NAT modes; input validation. Fully offline with mocked AWS |
| Image tests | `make test-docker` (`REAL=1` for real services) | Docker | each template builds, runs as non-root, reaches *healthy*; core-api image ships the Flyway migrations; worker stays up |
| Integration (9) | `make up migrate-raw seed && make test-integration` | Docker | LocalStack bucket/queues/redrive; SQS round-trip of the contract example; S3 put/get; pgvector + all tables; seed users; department pre-filter query returns no other-department chunks |
| Lint | CI `devops-ci.yml` | — | hadolint, shellcheck, actionlint, tflint, checkov (report), Trivy (report) |
| Smoke | `make smoke URL=...` | running deployment | web health, login, `/users/me` (HR → HR+GENERAL), departments, one chat, wrong password → 401 |
| Load | `k6 run -e API=... manali_devops/load-test/chat.js` | k6 | 20 VUs × 5 min, p95 < 6 s, errors < 2% |
