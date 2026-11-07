resource "aws_security_group" "ecs_tasks" {
  name        = "ekm-${var.environment}-ecs-tasks"
  description = "Egress-only security group for ECS tasks in the staging VPC."
  vpc_id      = aws_vpc.main.id

  egress {
    description = "Allow ECS tasks to reach AWS services and dependencies."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "ekm-${var.environment}-ecs-tasks"
  }
}

resource "aws_security_group" "rds" {
  name        = "ekm-${var.environment}-rds"
  description = "PostgreSQL access only from ECS tasks."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from ECS tasks only."
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress {
    description = "Allow RDS response traffic."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "ekm-${var.environment}-rds"
  }
}

resource "aws_db_subnet_group" "postgres" {
  name       = "ekm-${var.environment}-postgres"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "ekm-${var.environment}-postgres"
  }
}

resource "aws_db_instance" "postgres" {
  identifier = "ekm-${var.environment}-postgres"

  engine         = "postgres"
  engine_version = "16.13"
  instance_class = "db.t4g.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = data.aws_kms_key.rds.arn

  db_name  = var.rds_database_name
  username = var.rds_master_username
  port     = 5432

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period = 7
  backup_window           = "08:00-08:30"
  maintenance_window      = "sun:08:30-sun:09:00"
  copy_tags_to_snapshot   = true

  auto_minor_version_upgrade = true
  apply_immediately          = true
  deletion_protection        = false
  skip_final_snapshot        = true

  tags = {
    Name = "ekm-${var.environment}-postgres"
  }
}
