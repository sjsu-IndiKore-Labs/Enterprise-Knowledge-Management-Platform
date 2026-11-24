resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Public ALB"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from anywhere (redirects to HTTPS when a certificate is configured)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  count             = var.certificate_arn != "" ? 1 : 0
  security_group_id = aws_security_group.alb.id
  description       = "HTTPS from anywhere"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_tasks" {
  security_group_id            = aws_security_group.alb.id
  description                  = "ALB to ECS tasks"
  referenced_security_group_id = aws_security_group.ecs_tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 8081
}

resource "aws_security_group" "ecs_tasks" {
  name        = "${local.name}-ecs-tasks"
  description = "All EKM ECS tasks. Ingress only from the ALB and from each other."
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-ecs-tasks" }
}

resource "aws_vpc_security_group_ingress_rule" "tasks_from_alb" {
  for_each                     = toset(local.public_services)
  security_group_id            = aws_security_group.ecs_tasks.id
  description                  = "ALB to ${each.key}"
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = var.services[each.key].port
  to_port                      = var.services[each.key].port
}

resource "aws_vpc_security_group_ingress_rule" "tasks_from_tasks" {
  security_group_id            = aws_security_group.ecs_tasks.id
  description                  = "Service-to-service (core-api -> ai-service via Service Connect)"
  referenced_security_group_id = aws_security_group.ecs_tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 3000
  to_port                      = 8081
}

resource "aws_vpc_security_group_egress_rule" "tasks_all" {
  security_group_id = aws_security_group.ecs_tasks.id
  description       = "Outbound (ECR, OpenAI, AWS APIs)"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds"
  description = "PostgreSQL, only from ECS tasks"
  vpc_id      = aws_vpc.this.id
  tags        = { Name = "${local.name}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_tasks" {
  security_group_id            = aws_security_group.rds.id
  description                  = "Postgres from ECS tasks"
  referenced_security_group_id = aws_security_group.ecs_tasks.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}
