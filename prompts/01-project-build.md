# AI Prompt 01 — Full Project Build

**Tool:** Claude Code (claude-sonnet-4-6)  
**Date:** 2026-05-02  
**Purpose:** Transform an existing Spring Cloud Library backend into a production-ready, AWS-deployable project.

---

## Prompt

You are helping me complete my SWE455 Cloud Applications Engineering course project.

I have a Maven multi-module Spring Cloud backend project with these modules:

- api-gateway
- user-service
- book-service
- eureka-server

The app is a library backend. user-service handles signup, login, JWT authentication, user status, and admin user management. book-service handles catalog operations for printed books, e-books, and audiobooks. api-gateway routes /auth/** to user-service and /book/** to book-service. The app currently uses MariaDB, Eureka, JWT, and Kafka for user-name-change events.

Project requirements:
1. Backend architecture must contain at least two functional services and one data storage service.
2. Every cloud component must be provisioned using Terraform. Manual AWS Console configuration is prohibited.
3. Implement complete CI/CD. Any push to the repository must build container images and deploy automatically to production.
4. Clearly demonstrate how the application satisfies the 15-Factor methodology.
5. Deliver source code repos, cloud infrastructure repo/code, technical report, REST API documentation, architecture diagram, and AI prompt files.
6. During the demo, I must be able to delete the entire cloud environment and restore it to a working state within minutes using only code/scripts.

Your task:
Modify and extend this project so it becomes production-deployable on AWS using Terraform and GitHub Actions.

Required implementation:

1. Clean the repo:
   - Remove target folders from version control.
   - Add a proper .gitignore.
   - Standardize Java version across all modules, preferably Java 21.
   - Make sure mvn clean package works from the root.

2. Containerization:
   - Add Dockerfiles for api-gateway, user-service, book-service, and eureka-server if needed.
   - Use multi-stage Docker builds.
   - Add health checks where appropriate.
   - Ensure logs go to stdout/stderr.

3. Configuration:
   - Remove hardcoded localhost/internal URLs.
   - Move all environment-specific config to environment variables.
   - Required env vars include:
     DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD,
     JWT_SECRET, USER_SERVICE_URL, EUREKA_URL,
     AWS_REGION, USER_NAME_CHANGES_QUEUE_URL

4. Replace Kafka:
   - Replace Kafka-based user-name-change events with AWS SQS.
   - user-service should publish a JSON UserNameChangedEvent to SQS when an admin changes a user name.
   - book-service should consume/poll the SQS queue and update stored creator names.
   - Add AWS SDK dependencies as needed.
   - Make the queue URL configurable through env vars.
   - Make the code also work locally through docker-compose using LocalStack.

5. Health and graceful shutdown:
   - Add Spring Boot Actuator to every service.
   - Expose health endpoints.
   - Enable graceful shutdown (server.shutdown=graceful, 30s timeout).

6. Docker Compose:
   - Update docker-compose.yml so the whole system can run locally.
   - Include MariaDB.
   - Use LocalStack for SQS.

7. Terraform (infra/ directory):
   - VPC with public/private subnets, IGW, NAT Gateway
   - Security groups
   - ECR repositories for each service
   - ECS Fargate cluster, task definitions, and services
   - Application Load Balancer with listener rules for /auth/* and /book/*
   - RDS MariaDB
   - SQS queue
   - CloudWatch log groups
   - IAM roles and policies
   - SSM Parameter Store for JWT_SECRET and DB_PASSWORD
   - variables.tf, outputs.tf, terraform.tfvars.example

8. GitHub Actions (.github/workflows/deploy.yml):
   - Trigger on push to main.
   - Configure AWS credentials using OIDC.
   - Build Docker images.
   - Push images to ECR.
   - Run Terraform init and apply.
   - Update ECS services.

9. REST API documentation (docs/api-documentation.md):
   - Document all endpoints with method, path, auth, request/response bodies, and curl examples.

10. Technical report (docs/technical-report.md):
    - Project overview, architecture description, Mermaid diagram, cloud resources, CI/CD, 15-Factor table,
      deployment instructions, destroy/restore instructions, testing instructions, known limitations.

11. Prompt files (prompts/ directory):
    - Save this prompt as prompts/01-project-build.md.

Make all changes directly in the repo. Prefer simple, working, demonstrable solutions.

---

## Key Decisions Made by the AI

| Decision | Rationale |
|---|---|
| AWS SDK v2 (`software.amazon.awssdk:sqs:2.27.21`) | Current, actively maintained SDK |
| `@Scheduled` polling for SQS consumer | Simpler than Spring Cloud AWS; no extra dependency |
| Long polling (`waitTimeSeconds=20`) | Reduces empty-receive API calls |
| LocalStack for local SQS | Identical API to real AWS; no code changes between envs |
| SSM SecureString for secrets | Avoids secrets in Terraform state plaintext |
| OIDC for GitHub Actions | No long-lived AWS access keys stored in GitHub |
| Spring Boot layertools multi-stage Dockerfile | Better Docker layer caching for faster CI builds |
| Single ALB → api-gateway (all traffic) | Simplest routing; api-gateway handles /auth vs /book internally |
| `force_new_deployment = true` on ECS services | Ensures every Terraform apply triggers a container restart |
