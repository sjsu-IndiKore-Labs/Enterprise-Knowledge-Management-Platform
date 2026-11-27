resource "aws_sqs_queue" "dlq" {
  for_each                  = local.queues
  name                      = "${local.queue_names[each.key]}-dlq"
  message_retention_seconds = 1209600 # 14 days
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "this" {
  for_each                   = local.queues
  name                       = local.queue_names[each.key]
  visibility_timeout_seconds = each.value.visibility_timeout
  receive_wait_time_seconds  = 20     # long polling
  message_retention_seconds  = 345600 # 4 days
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 3 # contracts/events/README.md
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  for_each = {
    for k in local.queues : k => k
    if contains(keys(aws_sqs_queue.this), k)
  }
  queue_url = aws_sqs_queue.dlq[each.key].id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.this[each.key].arn]
  })
}
