# Operations runbook: alarms → actions

All alarms go to the SNS topic `ekm-staging-alarms` (email). Dashboard: `terraform output cloudwatch_dashboard_url`.

| Alarm | Likely cause | First steps |
|---|---|---|
| `*-dlq-not-empty` | A message failed 3 times (bad file, bug, OpenAI down) | Read it: `aws sqs receive-message --queue-url <dlq url>`. Check the consumer's logs by `traceId`. After the fix, redrive: SQS console → DLQ → *Start DLQ redrive* |
| `ingest-backlog-age` | ai-worker down or too slow | `aws ecs describe-services --cluster ekm-staging --services ekm-staging-ai-worker`; logs `/ecs/ekm-staging/ai-worker`; scale up desired count |
| `alb-5xx` | core-api or web errors / crash loop | ECS service events; logs filtered by `"level":"ERROR"`; roll back with `ecs-deploy.sh staging <previous sha>` |
| `core-api-p95-latency` | Slow OpenAI responses, slow SQL, CPU starved | Check ai-service latency in logs, RDS CPU, ECS CPU; autoscaling max is 3 |
| `*-unhealthy-targets` | Health check failing (`/api/health`, `/actuator/health`) | Task stopped reason in ECS console; missing secret / DB unreachable are the usual causes |
| `*-cpu-high` / `*-memory-high` | Under-sized task | Raise `cpu`/`memory` in `var.services` and apply; JVM uses 75% of task memory |
| `rds-cpu-high` / `rds-free-storage-low` | Missing index, huge corpus | `pg_stat_statements` / slow query log (>1 s); storage autoscaling goes up to 2× |
| Budget email | Costs above plan | `make aws-sleep` when idle; check Cost Explorer by service |

## Useful commands
```bash
aws logs tail /ecs/ekm-staging/core-api --follow --filter-pattern '{ $.level = "ERROR" }'
aws ecs list-tasks --cluster ekm-staging --service-name ekm-staging-core-api
aws ecs execute-command --cluster ekm-staging --task <task-id> --container core-api --interactive --command sh
manali_devops/scripts/ecs-deploy.sh staging <git-sha> core-api        # roll forward/back one service
```
