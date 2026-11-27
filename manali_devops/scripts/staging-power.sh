#!/usr/bin/env bash
# Save money when nobody is using staging: scale every ECS service to 0 and stop RDS (and back).
#   staging-power.sh down [env]   -> ECS desired 0 (autoscaling min 0), RDS stopped (AWS restarts it after 7 days!)
#   staging-power.sh up   [env]   -> RDS started, ECS desired 1 (autoscaling min 1), waits until stable
set -euo pipefail
ACTION="${1:?up|down}"; ENVIRONMENT="${2:-staging}"
PREFIX="${PROJECT:-ekm}-${ENVIRONMENT}"; REGION="${AWS_REGION:-us-west-2}"
SERVICES=(web core-api audit-service ai-service ai-worker)
COUNT=0; [ "$ACTION" = up ] && COUNT=1

if [ "$ACTION" = up ]; then
  status="$(aws rds describe-db-instances --region "$REGION" --db-instance-identifier "$PREFIX" --query 'DBInstances[0].DBInstanceStatus' --output text)"
  if [ "$status" = stopped ]; then
    aws rds start-db-instance --region "$REGION" --db-instance-identifier "$PREFIX" >/dev/null
  fi
  echo "waiting for RDS ${PREFIX} to be available..."
  aws rds wait db-instance-available --region "$REGION" --db-instance-identifier "$PREFIX"
fi

for svc in "${SERVICES[@]}"; do
  name="${PREFIX}-${svc}"
  if aws application-autoscaling describe-scalable-targets --region "$REGION" --service-namespace ecs \
       --resource-ids "service/${PREFIX}/${name}" --query 'ScalableTargets[0]' --output text | grep -q ecs; then
    aws application-autoscaling register-scalable-target --region "$REGION" --service-namespace ecs \
      --resource-id "service/${PREFIX}/${name}" --scalable-dimension ecs:service:DesiredCount --min-capacity "$COUNT" >/dev/null
  fi
  aws ecs update-service --region "$REGION" --cluster "$PREFIX" --service "$name" --desired-count "$COUNT" >/dev/null
  echo "${name} -> desired ${COUNT}"
done

if [ "$ACTION" = down ]; then
  aws rds stop-db-instance --region "$REGION" --db-instance-identifier "$PREFIX" >/dev/null && echo "RDS ${PREFIX} stopping"
else
  aws ecs wait services-stable --region "$REGION" --cluster "$PREFIX" --services "${SERVICES[@]/#/${PREFIX}-}"
  echo "staging is up"
fi
