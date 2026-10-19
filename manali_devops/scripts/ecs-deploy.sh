#!/bin/bash
# Usage: ecs-deploy.sh <cluster> <image-tag>
# TODO(Manali, M4): register new task definition revisions with the image tag, then update each service.
set -euo pipefail
CLUSTER="$1"; TAG="$2"
for svc in core-api audit-service ai-service web; do
  echo "would deploy ekm-${svc}:${TAG} to ${CLUSTER} (implement in Milestone 4)"
done
