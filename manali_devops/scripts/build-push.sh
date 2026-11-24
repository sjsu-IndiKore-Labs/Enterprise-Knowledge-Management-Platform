#!/usr/bin/env bash
# Build every service image that exists in the repo from the Dockerfile templates and push it to ECR.
# Prints the list of pushed services (one per line) on stdout's last line as "PUSHED: a b c".
#
# Usage: build-push.sh <environment> <image-tag>
# Env:   ECR_REGISTRY (required), PLATFORM (default linux/amd64), PUSH=0 to build only.
set -euo pipefail
ENVIRONMENT="${1:?environment}"; TAG="${2:?tag}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
PREFIX="${PROJECT:-ekm}-${ENVIRONMENT}"
PLATFORM="${PLATFORM:-linux/amd64}"
REG="${ECR_REGISTRY:?ECR_REGISTRY}"
D=manali_devops/docker
OUT="--push"; [ "${PUSH:-1}" = 0 ] && OUT="--load"
pushed=()

build() { # name dockerfile context [build-args...]
  local name="$1" file="$2" ctx="$3"; shift 3
  echo "==> building ${name}"
  docker buildx build --platform "$PLATFORM" -f "$file" "$@" \
    --label "org.opencontainers.image.revision=${TAG}" \
    -t "${REG}/${PREFIX}/${name}:${TAG}" $OUT "$ctx"
  pushed+=("$name")
}

if [ -f ayushi_backend/settings.gradle.kts ] || [ -f ayushi_backend/settings.gradle ]; then
  build core-api "$D/java-service.Dockerfile" . --build-arg SERVICE=core-api --build-arg PORT=8080
  build audit-service "$D/java-service.Dockerfile" . --build-arg SERVICE=audit-service --build-arg PORT=8081
fi
if [ -f vedanti_ai/pyproject.toml ] || [ -f vedanti_ai/requirements.txt ]; then
  build ai-service "$D/python-service.Dockerfile" vedanti_ai
fi
if [ -f changhyun_frontend/package.json ]; then
  # Empty API base URL: the browser calls /api/v1 on the same origin; the ALB routes it to core-api.
  build web "$D/node-web.Dockerfile" changhyun_frontend --build-arg NEXT_PUBLIC_API_BASE_URL=
fi
echo "PUSHED: ${pushed[*]:-}"
