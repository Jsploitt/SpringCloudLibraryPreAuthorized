# Infrastructure — Terraform

All AWS resources for the Spring Cloud Library project are provisioned here.

## Prerequisites

- Terraform ≥ 1.5 (`terraform version`)
- AWS CLI configured with sufficient permissions (`aws sts get-caller-identity`)
- An existing S3 bucket if you enable the remote backend in `main.tf`

## Quick Start

```bash
cd infra

# 1. Copy and fill in the variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars

# 2. Initialize providers and modules
terraform init

# 3. Preview changes
terraform plan

# 4. Apply (creates all AWS resources)
terraform apply

# 5. Get the public ALB URL
terraform output alb_dns_name
```

## Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `aws_region` | No | `us-east-1` | AWS region |
| `project_name` | No | `library` | Prefix for all resource names |
| `environment` | No | `prod` | Environment tag |
| `ecr_image_tag` | No | `latest` | Docker image tag to deploy |
| `db_username` | No | `libadmin` | RDS master username |
| `db_password` | **Yes** | — | RDS master password |
| `jwt_secret` | **Yes** | — | JWT signing secret (hex) |
| `db_instance_class` | No | `db.t3.micro` | RDS instance size |

## Outputs

| Output | Description |
|---|---|
| `alb_dns_name` | ALB DNS name — the API base URL |
| `ecr_urls` | ECR repository URLs |
| `sqs_queue_url` | SQS queue URL for user-name changes |
| `rds_endpoint` | RDS MariaDB endpoint |
| `ecs_cluster_name` | ECS cluster name |

## Destroy

```bash
terraform destroy -auto-approve
```

Removes everything. Re-running `terraform apply` restores the full environment.

## GitHub Actions OIDC Setup

Before the CI/CD pipeline can authenticate with AWS, create an IAM OIDC provider and role:

```bash
# 1. Create OIDC provider (one time per account)
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

# 2. Create IAM role with trust policy for your repo (see docs for full policy)
# Store the role ARN as GitHub secret: AWS_ROLE_ARN
```

Required GitHub secrets:

| Secret | Description |
|---|---|
| `AWS_ROLE_ARN` | IAM role ARN for OIDC authentication |
| `AWS_ACCOUNT_ID` | Your 12-digit AWS account ID |
| `DB_PASSWORD` | Database password (injected as Terraform variable) |
| `JWT_SECRET` | JWT signing secret (injected as Terraform variable) |
| `DB_USERNAME` | Database username |
