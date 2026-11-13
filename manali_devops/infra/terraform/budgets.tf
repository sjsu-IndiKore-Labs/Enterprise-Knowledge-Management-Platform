data "aws_caller_identity" "current" {}

resource "aws_budgets_budget" "monthly_aws" {
  provider = aws.billing

  name         = "ekm-${var.environment}-monthly-aws"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_aws_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"
  account_id   = data.aws_caller_identity.current.account_id

  dynamic "notification" {
    for_each = var.budget_notification_email == "" ? [] : ["actual"]

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = 80
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_notification_email]
    }
  }

  dynamic "notification" {
    for_each = var.budget_notification_email == "" ? [] : ["forecasted"]

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = 100
      threshold_type             = "PERCENTAGE"
      notification_type          = "FORECASTED"
      subscriber_email_addresses = [var.budget_notification_email]
    }
  }
}
