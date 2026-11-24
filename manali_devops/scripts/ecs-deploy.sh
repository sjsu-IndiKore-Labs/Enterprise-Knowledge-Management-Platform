#!/usr/bin/env bash
# Deploy an image tag to ECS services by cloning the latest task definition revision of each
# family with the new image, registering it, updating the service, and waiting until stable.
# Used by GitHub Actions (cd-staging.yml) and Jenkins.
#
# Usage: ecs-deploy.sh <environment> <image-tag> [service ...]
#   services default: web core-api audit-service ai-service ai-worker
# Env:   AWS_REGION (default us-west-2), PROJECT (default ekm), ECR_REGISTRY (default: derived from account)
#        SKIP_WAIT=1 to not wait for services-stable, DRY_RUN=1 to only print the new task definitions.
set -euo pipefail

ENVIRONMENT="${1:?environment (e.g. staging)}"
TAG="${2:?image tag (git sha)}"
shift 2
SERVICES=("$@")
[ ${#SERVICES[@]} -eq 0 ] && SERVICES=(web core-api audit-service ai-service ai-worker)

PROJECT="${PROJECT:-ekm}"
REGION="${AWS_REGION:-us-west-2}"
PREFIX="${PROJECT}-${ENVIRONMENT}"
CLUSTER="${PREFIX}"
if [ -z "${ECR_REGISTRY:-}" ]; then
  ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
  ECR_REGISTRY="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
fi

image_repo() { # ai-worker shares the ai-service image
  case "$1" in ai-worker) echo "ai-service" ;; *) echo "$1" ;; esac
}

deployed=()
for svc in "${SERVICES[@]}"; do
  family="${PREFIX}-${svc}"
  image="${ECR_REGISTRY}/${PREFIX}/$(image_repo "$svc"):${TAG}"
  echo "==> ${svc}: ${image}"

  current="$(aws ecs describe-task-definition --region "$REGION" --task-definition "$family" --query taskDefinition --output json)"
  new_def="$(echo "$current" | jq --arg IMAGE "$image" --arg NAME "$svc" '
      .containerDefinitions |= map(if .name == $NAME then .image = $IMAGE else . end)
      | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities,
            .registeredAt, .registeredBy, .deregisteredAt)')"

  if [ "${DRY_RUN:-0}" = 1 ]; then
    echo "$new_def" | jq '{family, image: .containerDefinitions[0].image}'
    continue
  fi

  new_arn="$(aws ecs register-task-definition --region "$REGION" --cli-input-json "$new_def" \
    --query taskDefinition.taskDefinitionArn --output text)"
  aws ecs update-service --region "$REGION" --cluster "$CLUSTER" --service "${PREFIX}-${svc}" \
    --task-definition "$new_arn" --force-new-deployment --query 'service.serviceName' --output text >/dev/null
  echo "    registered ${new_arn##*/}, service updated"
  deployed+=("${PREFIX}-${svc}")
done

if [ "${DRY_RUN:-0}" != 1 ] && [ "${SKIP_WAIT:-0}" != 1 ] && [ ${#deployed[@]} -gt 0 ]; then
  echo "==> waiting for services to become stable: ${deployed[*]}"
  aws ecs wait services-stable --region "$REGION" --cluster "$CLUSTER" --services "${deployed[@]}"
  echo "==> deployment stable"
fi
