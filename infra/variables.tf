variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Short project identifier used in resource names"
  type        = string
  default     = "library"
}

variable "environment" {
  description = "Deployment environment (prod, staging, dev)"
  type        = string
  default     = "prod"
}

variable "ecr_image_tag" {
  description = "Docker image tag to deploy (usually the git SHA)"
  type        = string
  default     = "latest"
}

# ── Database ──────────────────────────────────────────────────────────────────
variable "db_username" {
  description = "RDS master username"
  type        = string
  default     = "libadmin"
}

variable "db_password" {
  description = "RDS master password"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance type"
  type        = string
  default     = "db.t3.micro"
}

# ── Application ───────────────────────────────────────────────────────────────
variable "jwt_secret" {
  description = "Base64-encoded HS256 signing key for JWT"
  type        = string
  sensitive   = true
}

# ── Networking ────────────────────────────────────────────────────────────────
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "AZs to deploy into"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}
