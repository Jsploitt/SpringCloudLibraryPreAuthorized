# SpringCloudLibraryPreAuthorized — Cloud Engineering README

> **Scope note:** The Spring microservices application was already built.  
> This repository work focuses on everything done **after** application development: cloud infrastructure, deployment, and operations.

## What this repository delivers (post-app)

- Infrastructure as Code for AWS in [`/infra`](./infra)
- Containerized runtime on ECS Fargate
- Public ingress and routing through an Application Load Balancer
- Centralized logs in CloudWatch
- Secure secret delivery with SSM Parameter Store
- CI/CD automation in GitHub Actions with AWS OIDC (no long-lived AWS keys)

## Cloud architecture at a glance

- **Compute:** ECS Fargate services for:
  - `eureka-server`
  - `api-gateway`
  - `user-service`
  - `book-service`
- **Networking:** VPC + 2 public subnets + internet gateway + ALB
- **Container registry:** ECR repositories for each service
- **Messaging:** SQS queue for user-name-change events
- **Secrets:** JWT secret stored as `SecureString` in SSM
- **Observability:** per-service CloudWatch log groups

## Infrastructure provisioning (Terraform)

All AWS resources are managed from [`infra/`](./infra).

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars with your values
terraform init
terraform plan
terraform apply
terraform output alb_dns_name
```

For full variable and output details, see [`infra/README.md`](./infra/README.md).

## CI/CD pipeline

Workflow: [`.github/workflows/deploy.yml`](./.github/workflows/deploy.yml)

Trigger:
- Push to `main`
- Manual `workflow_dispatch`

Pipeline flow:
1. Build Maven modules
2. Assume AWS role via GitHub OIDC
3. Build and push Docker images to ECR
4. Force ECS service redeployments

## Required GitHub secrets

- `AWS_ROLE_ARN`
- `AWS_ACCOUNT_ID`
- `JWT_SECRET`
- `DB_USERNAME`
- `DB_PASSWORD`

(See [`infra/README.md`](./infra/README.md) for setup context.)

## Operations notes

- ALB DNS is the primary entrypoint (`terraform output alb_dns_name`)
- Eureka is exposed on ALB port `8761` for service-registry visibility
- `terraform destroy` tears down most resources, while OIDC bootstrap resources are protected in Terraform

## Important paths

- Terraform code: [`/infra`](./infra)
- Deployment workflow: [`/.github/workflows/deploy.yml`](./.github/workflows/deploy.yml)
- Technical project summary: [`technical-summary.md`](./technical-summary.md)
