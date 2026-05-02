resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id
  tags       = merge(local.common_tags, { Name = "${local.name_prefix}-db-subnet-group" })
}

resource "aws_db_parameter_group" "mariadb" {
  name   = "${local.name_prefix}-mariadb-params"
  family = "mariadb10.11"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }

  tags = local.common_tags
}

resource "aws_db_instance" "main" {
  identifier              = "${local.name_prefix}-mariadb"
  engine                  = "mariadb"
  engine_version          = "10.11"
  instance_class          = var.db_instance_class
  allocated_storage       = 20
  max_allocated_storage   = 100
  storage_type            = "gp3"
  storage_encrypted       = true

  db_name  = "librarydb"
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  parameter_group_name   = aws_db_parameter_group.mariadb.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 7
  skip_final_snapshot     = true
  deletion_protection     = false
  publicly_accessible     = false

  tags = merge(local.common_tags, { Name = "${local.name_prefix}-mariadb" })
}

# ── SSM Parameter Store ───────────────────────────────────────────────────────
resource "aws_ssm_parameter" "db_password" {
  name  = "/${local.name_prefix}/db_password"
  type  = "SecureString"
  value = var.db_password
  tags  = local.common_tags
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${local.name_prefix}/jwt_secret"
  type  = "SecureString"
  value = var.jwt_secret
  tags  = local.common_tags
}
