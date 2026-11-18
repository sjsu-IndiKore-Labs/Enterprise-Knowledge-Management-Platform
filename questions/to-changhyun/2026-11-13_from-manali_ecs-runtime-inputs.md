---
from: manali
to: changhyun
status: open
priority: blocking
created: 2026-11-13
related: manali_devops/infra/terraform/ecs.tf, contracts/ports-and-services.md
---

# ECS runtime inputs for web

The ECS/Fargate Terraform foundation is ready to deploy the four staging services. Before I
apply it, please provide or confirm:

1. `changhyun_frontend/Dockerfile` using the agreed Next.js standalone runtime.
2. The container startup command and confirmation that port `3000` serves `GET /api/health`.
3. The build-time handling for `NEXT_PUBLIC_API_BASE_URL`; the staging ALB URL is not known
   until the ALB is created.
4. Any additional environment variables beyond `.env.example`.

Please reply in this file with the image/build and runtime details so the ECR image can be built
and the ECS task can be smoke-tested.
