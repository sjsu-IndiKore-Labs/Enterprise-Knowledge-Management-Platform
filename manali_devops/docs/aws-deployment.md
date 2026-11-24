# AWS deployment runbook (staging)

Everything below is done **once** by Manali (or whoever holds AWS admin). After that, every merge to `main` deploys automatically.

## 0. Prerequisites
- AWS account (academic credits) with an admin IAM user/SSO for Terraform, region **us-west-2**
- Tools: Terraform ≥ 1.9, AWS CLI v2, Docker (buildx), jq, GNU make
- `aws sts get-caller-identity` works in your shell

## 1. Bootstrap remote state (once per account)
```bash
cd manali_devops/infra/terraform/bootstrap
terraform init && terraform apply          # creates ekm-tfstate-<account> bucket + ekm-tfstate-lock table
```

## 2. Configure the staging environment
```bash
cd manali_devops/infra/terraform/environments
cp staging.backend.hcl.example staging.backend.hcl   # put the bucket name from step 1
cp staging.tfvars.example staging.tfvars             # alarm_email, budget, optional certificate_arn/app_domain
# If an existing documents bucket is being reused, set documents_bucket_name in staging.tfvars.
cd ../../../.. && make tf-init
```

If `documents_bucket_name` points to an existing bucket, import that bucket before the first full
apply so Terraform manages it instead of trying to create a duplicate:

```bash
terraform -chdir=manali_devops/infra/terraform import \
  -var-file=environments/staging.tfvars \
  aws_s3_bucket.documents ekm-documents-staging
```

## 3. Create ECR, push the first images, then apply everything
ECS services need an image to start, so create the registries first and push images tagged with the current commit:
```bash
terraform -chdir=manali_devops/infra/terraform apply -var-file=environments/staging.tfvars -target=aws_ecr_repository.this
REG=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.us-west-2.amazonaws.com
aws ecr get-login-password | docker login --username AWS --password-stdin $REG
ECR_REGISTRY=$REG manali_devops/scripts/build-push.sh staging $(git rev-parse HEAD)
```
Then apply the full stack with that tag (~15 min, RDS is the slow part):
```bash
cd manali_devops/infra/terraform
terraform plan  -var-file=environments/staging.tfvars -var image_tag=$(git rev-parse HEAD) -out=staging.tfplan
terraform apply staging.tfplan
terraform output          # app_url, github_deploy_role_arn, secret_ids, ...
```
> If a service has no code yet (no image pushed), its ECS service stays at 0 healthy tasks. That is expected; the next CD run fixes it.

## 4. Set the OpenAI key (never in git, never in tfvars)
```bash
aws secretsmanager put-secret-value --secret-id ekm/staging/openai-api-key --secret-string 'sk-...'
```

## 5. Connect GitHub Actions
Repository → Settings → Secrets and variables → **Variables**:

| Variable | Value |
|---|---|
| `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw github_deploy_role_arn` |
| `AWS_REGION` | `us-west-2` |
| `STAGING_URL` | `terraform output -raw app_url` |

Repository → Settings → Environments → create **staging** (optionally require a reviewer before deploys).

## 6. Deploy
- Automatic: merge to `main` → `.github/workflows/cd-staging.yml` builds every service that exists, pushes to ECR (tag = commit SHA), registers new task definitions, waits for the services to be stable, runs the smoke test.
- Manual from a laptop: `make deploy` (needs AWS creds; uses the same scripts).
- Manual from Actions: *CD staging → Run workflow* (optionally only some services).
- Jenkins: build with parameter `DEPLOY=true` on `main` (see [ci-cd.md](ci-cd.md)).

## 7. Load data and verify
```bash
make smoke URL=$(terraform -chdir=manali_devops/infra/terraform output -raw app_url)
# seed dev users into RDS (staging demo only) through a one-off ECS Exec into core-api:
aws ecs execute-command --cluster ekm-staging --task <core-api task id> --container core-api --interactive --command "sh"
make ingest-corpus API=$(terraform -chdir=manali_devops/infra/terraform output -raw app_url)
```
Database migrations run automatically: core-api applies Flyway on startup (migrations are baked into the image).

## 8. Day-2 operations
- Logs: CloudWatch → Log groups `/ecs/ekm-staging/<service>` (JSON; search by `traceId`). Or `aws logs tail /ecs/ekm-staging/core-api --follow`.
- Shell into a container: `aws ecs execute-command --cluster ekm-staging --task <id> --container <service> --interactive --command sh`
- Rollback: re-run *CD staging* for an older commit, or `manali_devops/scripts/ecs-deploy.sh staging <old-sha>`. Failed deployments roll back automatically (circuit breaker).
- Pause to save money: `make aws-sleep` / `make aws-wake`.
- Alarms and what to do: [operations-runbook.md](operations-runbook.md).

## 9. Teardown (after grading)
```bash
make aws-wake   # RDS must be running to be deleted
terraform -chdir=manali_devops/infra/terraform destroy -var-file=environments/staging.tfvars
# then optionally: terraform -chdir=manali_devops/infra/terraform/bootstrap destroy  (empty the state bucket first)
```
