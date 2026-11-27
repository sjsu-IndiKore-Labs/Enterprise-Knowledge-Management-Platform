#!/usr/bin/env bash
# Builds every Dockerfile template against its fixture app (or the real service when
# REAL=1 and the service exists), starts the container, and waits for a healthy status.
# Also asserts the container does not run as root.
#
#   manali_devops/tests/docker/test_images.sh            # all templates, fixtures
#   manali_devops/tests/docker/test_images.sh python     # one template
#   REAL=1 manali_devops/tests/docker/test_images.sh     # use real service folders that exist
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
FIX="manali_devops/tests/fixtures"
DOCKERFILES="$ROOT/manali_devops/docker"
ONLY="${1:-all}"
FAILED=0
cd "$ROOT"

wait_healthy() { # name timeout
  local name="$1" timeout="$2" status=""
  for _ in $(seq 1 "$timeout"); do
    status="$(docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null || echo missing)"
    [ "$status" = "healthy" ] && return 0
    [ "$status" = "unhealthy" ] && break
    sleep 1
  done
  echo "  status=$status"; docker logs --tail 40 "$name" || true
  return 1
}

check() { # label image port [docker run args...]
  local label="$1" image="$2" port="$3"; shift 3
  local name="ekm-imgtest-$label"
  docker rm -f "$name" >/dev/null 2>&1 || true
  docker run -d --name "$name" -e PORT="$port" "$@" "$image" >/dev/null
  local user; user="$(docker exec "$name" id -u)"
  if [ "$user" = "0" ]; then echo "FAIL $label runs as root"; FAILED=1;
  elif wait_healthy "$name" 180; then echo "PASS $label (uid $user, healthy)";
  else echo "FAIL $label not healthy"; FAILED=1; fi
  docker rm -f "$name" >/dev/null 2>&1 || true
}

# Real services connect to Postgres/SQS at startup. For REAL=1, start them first with
# `docker compose up -d --wait postgres localstack` and set BACKEND_NETWORK=ekm_default.
infra_run_args=()
if [ -n "${BACKEND_NETWORK:-}" ]; then
  infra_run_args=(--network "$BACKEND_NETWORK"
    -e POSTGRES_HOST=postgres -e POSTGRES_PORT=5432 -e POSTGRES_DB=ekm
    -e POSTGRES_USER=ekm -e POSTGRES_PASSWORD=ekm_local_password
    -e AWS_ENDPOINT_URL=http://localstack:4566 -e AWS_REGION=us-west-2
    -e AWS_ACCESS_KEY_ID=test -e AWS_SECRET_ACCESS_KEY=test
    -e JWT_SECRET=image-test-jwt-secret-at-least-32-bytes-long
    -e INTERNAL_SERVICE_TOKEN=image-test-internal-token)
fi

if [[ "$ONLY" == all || "$ONLY" == python ]]; then
  ctx="$FIX/python-service"; [ "${REAL:-0}" = 1 ] && [ -f vedanti_ai/pyproject.toml ] && ctx="vedanti_ai"
  echo "== python-service.Dockerfile ($ctx)"
  docker build -q -f "$DOCKERFILES/python-service.Dockerfile" -t ekm-test/ai-service "$ctx" >/dev/null
  check ai-service ekm-test/ai-service 8000 -e AI_PROVIDER=fake
  # worker: same image, different command; must stay running
  docker rm -f ekm-imgtest-worker >/dev/null 2>&1 || true
  docker run -d --name ekm-imgtest-worker --no-healthcheck -e AI_PROVIDER=fake \
    ${infra_run_args[@]+"${infra_run_args[@]}"} ekm-test/ai-service python -m app.ingestion.worker >/dev/null
  sleep 5
  if [ "$(docker inspect -f '{{.State.Running}}' ekm-imgtest-worker)" = true ]; then echo "PASS ai-worker (running)"; else echo "FAIL ai-worker exited"; docker logs ekm-imgtest-worker; FAILED=1; fi
  docker rm -f ekm-imgtest-worker >/dev/null
fi

if [[ "$ONLY" == all || "$ONLY" == node ]]; then
  ctx="$FIX/node-web"; [ "${REAL:-0}" = 1 ] && [ -f changhyun_frontend/package.json ] && ctx="changhyun_frontend"
  echo "== node-web.Dockerfile ($ctx)"
  docker build -q -f "$DOCKERFILES/node-web.Dockerfile" --build-arg NEXT_PUBLIC_API_BASE_URL= -t ekm-test/web "$ctx" >/dev/null
  check web ekm-test/web 3000
fi

if [[ "$ONLY" == all || "$ONLY" == java ]]; then
  dir="$FIX/java-backend"; [ "${REAL:-0}" = 1 ] && [ -f ayushi_backend/pom.xml ] && dir="ayushi_backend"
  java_run_args=()
  if [ "$dir" = ayushi_backend ]; then
    : "${BACKEND_NETWORK:?REAL=1 backend test needs BACKEND_NETWORK (e.g. ekm_default) with postgres + localstack running}"
    java_run_args=("${infra_run_args[@]}")
  fi
  for spec in core-api:8080 audit-service:8081; do
    svc="${spec%%:*}"; port="${spec#*:}"
    echo "== java-service.Dockerfile ($dir, $svc)"
    docker build -q -f "$DOCKERFILES/java-service.Dockerfile" --build-arg BACKEND_DIR="$dir" \
      --build-arg SERVICE="$svc" --build-arg PORT="$port" -t "ekm-test/$svc" . >/dev/null
    check "$svc" "ekm-test/$svc" "$port" ${java_run_args[@]+"${java_run_args[@]}"}
    if [ "$svc" = core-api ]; then
      if docker run --rm --entrypoint ls "ekm-test/$svc" /contracts/database/migrations | grep -q V1__init_schema.sql; then
        echo "PASS core-api image contains Flyway migrations"
      else
        echo "FAIL migrations missing"; FAILED=1
      fi
    fi
  done
fi

exit $FAILED
