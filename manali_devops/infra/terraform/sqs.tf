locals {
  sqs_queues = {
    document_ingest = {
      name               = "ekm-${var.environment}-document-ingest"
      visibility_timeout = 300
    }
    document_status = {
      name               = "ekm-${var.environment}-document-status"
      visibility_timeout = 60
    }
    audit_events = {
      name               = "ekm-${var.environment}-audit-events"
      visibility_timeout = 60
    }
  }
}

resource "aws_sqs_queue" "dlq" {
  for_each = local.sqs_queues

  name                    = "${each.value.name}-dlq"
  sqs_managed_sse_enabled = true
}

resource "aws_sqs_queue" "main" {
  for_each = local.sqs_queues

  name                       = each.value.name
  visibility_timeout_seconds = each.value.visibility_timeout
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 3
  })
}

output "sqs_queue_urls" {
  description = "URLs for the staging SQS queues."
  value       = { for key, queue in aws_sqs_queue.main : key => queue.url }
}

output "sqs_dlq_urls" {
  description = "URLs for the staging SQS dead-letter queues."
  value       = { for key, queue in aws_sqs_queue.dlq : key => queue.url }
}
