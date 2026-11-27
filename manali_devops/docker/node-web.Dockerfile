# syntax=docker/dockerfile:1.7
# =============================================================================
# Next.js web portal. Owner: Manali.
# Build context: changhyun_frontend/
#
#   docker build -f manali_devops/docker/node-web.Dockerfile \
#     --build-arg NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 -t ekm-web changhyun_frontend
#
# Expects (Changhyun): next.config with `output: 'standalone'`, a `build` script,
# GET /api/health route. NEXT_PUBLIC_* values are baked in at BUILD time.
# In AWS we build with NEXT_PUBLIC_API_BASE_URL="" so the browser calls /api/v1/... on
# the same origin (the ALB routes /api/v1/* to core-api) — no CORS needed.
# =============================================================================
ARG NODE_IMAGE=node:20-alpine

FROM ${NODE_IMAGE} AS deps
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml* package-lock.json* ./
RUN if [ -f pnpm-lock.yaml ]; then pnpm install --frozen-lockfile --allow-build=esbuild; \
    elif [ -f package-lock.json ]; then npm ci; \
    else npm install --no-audit --no-fund; fi

FROM ${NODE_IMAGE} AS build
WORKDIR /app
RUN corepack enable
ARG NEXT_PUBLIC_API_BASE_URL=""
ARG NEXT_PUBLIC_APP_NAME="Helios Knowledge Portal"
ARG NEXT_PUBLIC_MAX_UPLOAD_MB=25
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL} \
    NEXT_PUBLIC_APP_NAME=${NEXT_PUBLIC_APP_NAME} \
    NEXT_PUBLIC_MAX_UPLOAD_MB=${NEXT_PUBLIC_MAX_UPLOAD_MB} \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN if [ -f pnpm-lock.yaml ]; then pnpm run build; else npm run build; fi
RUN if [ ! -f .next/standalone/server.js ]; then echo "ERROR: set output: 'standalone' in next.config" >&2; exit 1; fi \
 && mkdir -p public

FROM ${NODE_IMAGE}
ARG PORT=3000
LABEL org.opencontainers.image.title="ekm-web" \
      org.opencontainers.image.source="https://github.com/sjsu-IndiKore-Labs/Enterprise-Knowledge-Management-Platform"
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=${PORT} HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE ${PORT}
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/api/health" >/dev/null || exit 1
CMD ["node", "server.js"]
