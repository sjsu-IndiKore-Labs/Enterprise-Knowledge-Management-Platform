export const dynamic = 'force-dynamic';

// Liveness probe for the container HEALTHCHECK / docker-compose / ECS target group.
export function GET() {
  return Response.json({ status: 'ok' });
}
