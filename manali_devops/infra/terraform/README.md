# Terraform: AWS staging stack

Flat, single-environment stack (one `tfvars` per environment). Start with [../../docs/aws-deployment.md](../../docs/aws-deployment.md).

| File | Resources |
|---|---|
| `versions.tf`, `variables.tf`, `locals.tf`, `outputs.tf` | providers, inputs (sizing in `var.services`), naming from `contracts/ports-and-services.md` |
| `network.tf`, `security_groups.tf` | VPC, 2 public + 2 private subnets, optional NAT, S3 endpoint, SGs |
| `ecr.tf`, `ecs.tf`, `alb.tf` | registries, cluster, task definitions, services, Service Connect, autoscaling, load balancer |
| `s3.tf`, `sqs.tf`, `rds.tf`, `secrets.tf`, `iam.tf` | data stores, queues, secrets, least-privilege roles |
| `monitoring.tf`, `github_oidc.tf` | alarms, dashboard, SNS, budget; GitHub deploy role |
| `bootstrap/` | state bucket + lock table (run once) |
| `tests/stack.tftest.hcl` | offline unit tests (`make tf-test`) |

```bash
make tf-validate          # no AWS needed
make tf-test              # no AWS needed
make tf-init TF_ENV=staging && make tf-plan TF_ENV=staging && make tf-apply TF_ENV=staging
```
