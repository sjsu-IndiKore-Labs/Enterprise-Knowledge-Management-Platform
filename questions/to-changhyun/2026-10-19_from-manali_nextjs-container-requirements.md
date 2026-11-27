---
from: manali
to: changhyun
status: open
priority: normal
created: 2026-10-19
related: manali_devops/docker/node-web.Dockerfile
---

# Next.js settings needed for the Docker image and AWS

## Context
The web image is built by `manali_devops/docker/node-web.Dockerfile` (tested with a fixture Next 14 app).
In AWS the browser reaches web and core-api through **one** load balancer: `/api/v1/*` → core-api, everything else → web.

## Question
Can you make sure that:
1. `next.config.*` has `output: 'standalone'` (the build fails otherwise).
2. There is a `GET /api/health` route returning 200 (and no other web route under `/api/v1/...`, those go to core-api).
3. The API client builds URLs as `${NEXT_PUBLIC_API_BASE_URL}/api/v1/...` and works when the variable is an **empty string** (same origin).
4. A lockfile is committed (`pnpm-lock.yaml` preferred).

## Proposed answer
Yes to all four.

## Answer
