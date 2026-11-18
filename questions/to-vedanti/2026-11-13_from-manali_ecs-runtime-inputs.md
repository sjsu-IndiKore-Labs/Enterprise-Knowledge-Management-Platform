---
from: manali
to: vedanti
status: open
priority: blocking
created: 2026-11-13
related: manali_devops/infra/terraform/ecs.tf, contracts/ports-and-services.md
---

# ECS runtime inputs for ai-service

The ECS/Fargate Terraform foundation is ready to deploy the four staging services. Before I
apply it, please provide or confirm:

1. `vedanti_ai/Dockerfile` using the agreed Python 3.11 runtime.
2. The container startup command and confirmation that port `8000` serves `GET /health`.
3. Any additional environment variables beyond `.env.example`.
4. Confirmation that `AI_PROVIDER=openai` is the expected staging setting.

Please reply in this file with the image/build and runtime details so the ECR image can be built
and the ECS task can be smoke-tested.
