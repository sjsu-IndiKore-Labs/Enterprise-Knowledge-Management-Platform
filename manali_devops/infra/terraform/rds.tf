resource "aws_db_subnet_group" "this" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_parameter_group" "this" {
  name_prefix = "${local.name}-pg16-"
  family      = "postgres${split(".", var.db_engine_version)[0]}"

  parameter {
    name  = "rds.force_ssl"
    value = "1" # services connect with POSTGRES_SSLMODE=require
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000" # log queries slower than 1 s
  }

  lifecycle {
    create_before_destroy = true
  }
}

# pgvector: core-api's Flyway migration V1 runs `CREATE EXTENSION IF NOT EXISTS vector`
# (the RDS master user is allowed to create it on PostgreSQL 15+).
resource "aws_db_instance" "this" {
  identifier                   = local.name
  engine                       = "postgres"
  engine_version               = var.db_engine_version
  instance_class               = var.db_instance_class
  allocated_storage            = var.db_allocated_storage
  max_allocated_storage        = var.db_allocated_storage * 2
  storage_type                 = "gp3"
  storage_encrypted            = true
  db_name                      = "ekm"
  username                     = "ekm"
  password                     = random_password.db.result
  db_subnet_group_name         = aws_db_subnet_group.this.name
  parameter_group_name         = aws_db_parameter_group.this.name
  vpc_security_group_ids       = [aws_security_group.rds.id]
  publicly_accessible          = false
  multi_az                     = false
  backup_retention_period      = 7
  auto_minor_version_upgrade   = true
  deletion_protection          = var.db_deletion_protection
  skip_final_snapshot          = var.db_skip_final_snapshot
  final_snapshot_identifier    = var.db_skip_final_snapshot ? null : "${local.name}-final"
  copy_tags_to_snapshot        = true
  performance_insights_enabled = false
  apply_immediately            = true
}
