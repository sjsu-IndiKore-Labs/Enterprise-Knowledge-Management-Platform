#!/bin/bash
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-west-2}"
BUCKET="${S3_DOCUMENTS_BUCKET:-ekm-documents-local}"
INGEST="${SQS_DOCUMENT_INGEST_QUEUE:-ekm-document-ingest}"
STATUS="${SQS_DOCUMENT_STATUS_QUEUE:-ekm-document-status}"
AUDIT="${SQS_AUDIT_EVENTS_QUEUE:-ekm-audit-events}"

echo "[validate-localstack] checking S3 bucket ${BUCKET}"
awslocal s3api head-bucket --bucket "${BUCKET}"
cors=$(awslocal s3api get-bucket-cors --bucket "${BUCKET}" --output json)
echo "${cors}" | grep -F 'http://localhost:3000' >/dev/null
echo "${cors}" | grep -F 'GET' >/dev/null
echo "${cors}" | grep -F 'PUT' >/dev/null

check_queue() {
  local name="$1" visibility="$2" redrive="$3"
  local url attrs
  url=$(awslocal sqs get-queue-url --queue-name "${name}" --region "${REGION}" \
    --query 'QueueUrl' --output text)
  attrs=$(awslocal sqs get-queue-attributes --queue-url "${url}" --region "${REGION}" \
    --attribute-names VisibilityTimeout ReceiveMessageWaitTimeSeconds RedrivePolicy --output json)
  echo "${attrs}" | grep -F "\"VisibilityTimeout\": \"${visibility}\"" >/dev/null
  echo "${attrs}" | grep -F '"ReceiveMessageWaitTimeSeconds": "20"' >/dev/null
  if [ "${redrive}" = "true" ]; then
    echo "${attrs}" | grep -E 'maxReceiveCount.*3' >/dev/null
    awslocal sqs get-queue-url --queue-name "${name}-dlq" --region "${REGION}" >/dev/null
  fi
  echo "[validate-localstack] queue ${name} OK"
}

check_queue "${INGEST}" 300 true
check_queue "${STATUS}" 60 true
check_queue "${AUDIT}" 60 true

queue_count=$(awslocal sqs list-queues --region "${REGION}" --query 'length(QueueUrls)' --output text)
test "${queue_count}" -eq 6
echo "[validate-localstack] bucket, CORS, 3 queues, 3 DLQs, visibility, and redrive policies OK"
