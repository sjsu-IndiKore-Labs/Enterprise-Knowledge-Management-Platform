#!/bin/bash
# Runs inside the LocalStack container when it is ready (mounted at /etc/localstack/init/ready.d/).
# Resource names must match .env.example and contracts/ports-and-services.md.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-west-2}"
BUCKET="${S3_DOCUMENTS_BUCKET:-ekm-documents-local}"

echo "[init-aws] creating bucket ${BUCKET}"
awslocal s3api create-bucket --bucket "${BUCKET}" --region "${REGION}" \
  --create-bucket-configuration LocationConstraint="${REGION}" || true
awslocal s3api put-bucket-cors --bucket "${BUCKET}" --cors-configuration '{
  "CORSRules": [{"AllowedOrigins": ["http://localhost:3000"], "AllowedMethods": ["GET", "PUT"], "AllowedHeaders": ["*"], "MaxAgeSeconds": 3000}]
}'

create_queue_with_dlq() {
  local name="$1" visibility="$2"
  awslocal sqs create-queue --queue-name "${name}-dlq" --region "${REGION}" >/dev/null
  local dlq_arn
  dlq_arn=$(awslocal sqs get-queue-attributes --region "${REGION}" \
    --queue-url "http://localhost:4566/000000000000/${name}-dlq" \
    --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
  awslocal sqs create-queue --queue-name "${name}" --region "${REGION}" --attributes "{
    \"VisibilityTimeout\": \"${visibility}\",
    \"ReceiveMessageWaitTimeSeconds\": \"20\",
    \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${dlq_arn}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"
  }" >/dev/null
  echo "[init-aws] queue ${name} (+dlq) ready"
}

create_queue_with_dlq "${SQS_DOCUMENT_INGEST_QUEUE:-ekm-document-ingest}" 300
create_queue_with_dlq "${SQS_DOCUMENT_STATUS_QUEUE:-ekm-document-status}" 60
create_queue_with_dlq "${SQS_AUDIT_EVENTS_QUEUE:-ekm-audit-events}" 60

echo "[init-aws] done"
