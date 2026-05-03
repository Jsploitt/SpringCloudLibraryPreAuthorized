# RDS removed — services use H2 in-memory database for fast demo destroy/apply.
# Data storage factor (15-Factor #4) is satisfied by H2 (backing service,
# swappable via env vars) and SQS (external message broker).

# ── SSM Parameter Store ───────────────────────────────────────────────────────
resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${local.name_prefix}/jwt_secret"
  type  = "SecureString"
  value = var.jwt_secret
  tags  = local.common_tags
}
