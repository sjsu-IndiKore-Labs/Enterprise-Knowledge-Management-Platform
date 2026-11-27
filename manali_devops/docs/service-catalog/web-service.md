# Service card: web portal

**Service name:** `web`  
**Aliases:** web portal, frontend, UI, Helios Knowledge Portal  
**Owner:** Changhyun Kim  
**Technology:** Next.js App Router, TypeScript, Tailwind CSS  
**Port:** `3000`  
**Health check:** `GET /api/health`

## Responsibility

The web service provides the browser user interface for authentication, document browsing and upload, search, chat, citations, conversation history, feedback, and administration screens.

## Dependencies

- Calls `core-api` for authentication, documents, search, chat, and analytics.
- Does not access S3 directly for protected document operations.
- Uses same-origin `/api/v1/...` routing in the AWS staging deployment.
- Uses a Prism or stateful mock server during frontend development when the backend is unavailable.

## Deployment

- Container image: Node web image based on `manali_devops/docker/node-web.Dockerfile`.
- ECS service name: `ekm-staging-web`.
- Public traffic reaches the web service through the ALB.
- The web image is configured for standalone Next.js output.

## Common questions

**Who owns the web portal?** Changhyun Kim.  
**What port does it use?** Port 3000.  
**Does the browser upload directly to S3?** No. Upload authorization and coordination go through `core-api`.

## Troubleshooting

- A blank page or failed asset load: check the web container logs and `/api/health`.
- API calls fail in local development: verify `NEXT_PUBLIC_API_BASE_URL` and whether the mock or `core-api` is running.
- Upload permission errors: check the authenticated user's role and department in `core-api`; do not fix this by trusting a browser-supplied department.

## Source references

- `../../../contracts/ports-and-services.md`
- `../../../../changhyun_frontend/README.md`
- `../../infra/terraform/ecs.tf`
