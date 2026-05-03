# Technical Report — Spring Cloud Library (SWE455)

## 1. Project Overview

Spring Cloud Library is a production-grade library management backend built as a set of microservices. It handles user authentication, book catalog operations, and event-driven name-change propagation. The system runs on AWS using ECS Fargate containers, with full CI/CD automation via GitHub Actions and infrastructure managed entirely by Terraform.

Each service lives in its own Git repository under the **SWE455-proj-team** GitHub organisation, with an independent CI/CD pipeline that builds, publishes, and deploys automatically on every push to `main`.

---

## 2. Architecture Description

The application consists of four services:

| Service | Port | Responsibility |
|---|---|---|
| **eureka-server** | 8761 | Netflix Eureka service registry |
| **api-gateway** | 8080 | Spring Cloud Gateway MVC — routes `/auth/**` and `/book/**` |
| **user-service** | 8085 | Signup, login, JWT issuance, user status & name management |
| **book-service** | 8086 | Book CRUD, polymorphic book types, SQS consumer |

External clients reach the system exclusively through the Application Load Balancer (ALB). Traffic flows: `Client → ALB → api-gateway → user-service / book-service`. Services discover each other via Eureka using load-balanced RestTemplate.

Each service uses an **H2 database** embedded in its container. This eliminates the RDS dependency, reducing full destroy-and-restore time to roughly 5 minutes. The schema is created automatically on startup via `spring.jpa.hibernate.ddl-auto=create`, and the default admin account (`admin / Admin123!`) is seeded by `DataInitializer.java`.

User name-change events are published to an AWS SQS queue by user-service and consumed by book-service via a scheduled poll (every 5 seconds).

---

## 3. Architecture Diagram

```
          ┌──────────────────────────────────────────────────────────┐
          │                    AWS Cloud (us-east-1)                 │
          │                                                          │
          │   ┌─────────────────────────────────────────────────┐   │
          │   │           Application Load Balancer              │   │
          │   │               (port 80, public)                  │   │
          │   └──────────────────────┬──────────────────────────┘   │
          │                          │                               │
          │              ┌───────────▼───────────┐                  │
          │              │      api-gateway       │                  │
          │              │        :8080           │                  │
          │              └──────┬──────────┬──────┘                  │
          │                     │          │                          │
          │          /auth/**   │          │  /book/**               │
          │                     │          │                          │
          │    ┌────────────────▼──┐   ┌───▼────────────────┐       │
          │    │   user-service    │   │   book-service     │       │
          │    │      :8085        │   │      :8086         │       │
          │    │   [H2 in-mem DB]  │   │  [H2 in-mem DB]   │       │
          │    └────────┬──────────┘   └───────────┬────────┘       │
          │             │    publish event          │ poll events    │
          │             └──────────►  SQS Queue  ◄─┘                │
          │                        user-name-changes                  │
          │                                                          │
          │   All 4 services register with Eureka Server (:8761)    │
          │   All secrets (JWT_SECRET) stored in SSM Parameter Store │
          │   All logs streamed to CloudWatch Log Groups             │
          └──────────────────────────────────────────────────────────┘
```

**Local development** mirrors production exactly: docker-compose runs all four services plus LocalStack (SQS emulation). H2 databases are created automatically — no external database setup required.

---

## 4. Cloud Resources (Terraform-provisioned)

Every AWS resource below is created and managed by Terraform (in the `infra` repository). No AWS Console configuration is used.

| Resource | Purpose |
|---|---|
| VPC + Public Subnets (×2, multi-AZ) | Network isolation; all services in public subnets (no NAT needed) |
| Internet Gateway | Outbound internet access for ECS tasks pulling from ECR |
| Application Load Balancer | Single public entry point; routes `/auth/*` and `/book/*` |
| ECS Fargate Cluster | Serverless container runtime — no EC2 instances to manage |
| 4× ECS Task Definitions | One per service, Java 21 images pulled from ECR |
| 4× ECS Services | `desired_count = 1`; `force_new_deployment = true` |
| 4× ECR Repositories | Docker image storage with lifecycle cleanup |
| SQS Queue (`user-name-changes`) | Async event bus for user name-change propagation |
| 4× CloudWatch Log Groups | Centralised structured logs from all containers |
| IAM — ECS Task Execution Role | Allows ECS to pull images from ECR and write to CloudWatch |
| IAM — ECS Task Role | Grants containers access to SQS and SSM |
| IAM — GitHub Actions OIDC Role | Allows GitHub Actions to deploy without long-lived AWS keys |
| OIDC Identity Provider (GitHub) | Federates GitHub Actions tokens to AWS IAM (`prevent_destroy = true`) |
| SSM Parameter Store | `JWT_SECRET` stored as SecureString; injected at container startup |
| Security Groups | ALB (port 80 public) → ECS (ports 8080–8086 from ALB + VPC CIDR) |

> **Why public subnets / no NAT?** Removing NAT Gateway eliminates ~$32/month in AWS costs and allows `terraform destroy` to complete in ~2 minutes instead of 5+. ECS tasks pull images directly from ECR over the internet gateway. This is the correct trade-off for a demo/educational environment.

> **Why H2 instead of RDS?** RDS creation takes 8–10 minutes and costs money continuously. H2 starts in seconds, costs nothing, and the schema is recreated automatically on every deploy — enabling the full destroy-and-restore demo in under 5 minutes.

---

## 5. CI/CD Pipeline

The project uses **5 independent GitHub repositories** under the `SWE455-proj-team` organisation, each with its own GitHub Actions pipeline.

### Service Repositories (user-service, book-service, api-gateway, eureka-server)

On every `push` to `main`:

1. **Checkout** — source code cloned.
2. **Java 21 + Maven cache** — dependencies downloaded once and cached.
3. **`mvn clean package -DskipTests`** — fat JAR built.
4. **OIDC authentication** — ephemeral AWS credentials via `aws-actions/configure-aws-credentials`; no long-lived keys stored in GitHub.
5. **ECR login** — `aws-actions/amazon-ecr-login`.
6. **Docker build & push** — image tagged with the git SHA and `latest`, pushed to ECR.
7. **ECS force redeploy** — `aws ecs update-service --force-new-deployment` restarts the container with the new image.

### Infrastructure Repository (infra)

Three workflows:

| Workflow | Trigger | Action |
|---|---|---|
| `infra-apply.yml` | Push to `main` | `terraform init` + `terraform apply -auto-approve` |
| `infra-apply-manual.yml` | Manual (`workflow_dispatch`) | Same as above, on demand |
| `infra-destroy.yml` | Manual (`workflow_dispatch`) | `terraform destroy -auto-approve` |

All workflows authenticate to AWS via OIDC — the GitHub Actions IAM role trusts all five `SWE455-proj-team` repositories.

---

## 6. 15-Factor Methodology

| Factor | Implementation |
|---|---|
| **I. Codebase** | One repository per deployable service (`SWE455-proj-team/{service}`); each repo has exactly one CI/CD pipeline and is deployed independently |
| **II. Dependencies** | All dependencies declared in `pom.xml`; Docker image isolates the runtime — no implicit system packages |
| **III. Config** | All environment-specific config in environment variables (`JWT_SECRET`, `EUREKA_URL`, `AWS_REGION`, `USER_NAME_CHANGES_QUEUE_URL`, etc.); secrets injected from AWS SSM at container startup |
| **IV. Backing services** | SQS treated as an attached resource referenced via env vars (`USER_NAME_CHANGES_QUEUE_URL`); H2 is embedded per-container; LocalStack provides a drop-in SQS replacement locally |
| **V. Build/Release/Run** | Maven produces an immutable JAR (build) → Docker image tagged with git SHA (release) → ECS Fargate service (run); stages are strictly separated |
| **VI. Processes** | Stateless Spring Boot processes; no in-memory session state; all persistent state in H2 (per container) or SQS (shared) |
| **VII. Port binding** | Each service binds to `server.port` at startup and exports itself; no runtime HTTP server injection |
| **VIII. Concurrency** | ECS `desired_count` can be scaled per service independently; stateless processes allow horizontal scaling |
| **IX. Disposability** | Graceful shutdown enabled (`server.shutdown=graceful`, 30 s timeout); fast startup with layered Docker images and H2 (no waiting for external DB connection) |
| **X. Dev/Prod parity** | Same Docker images run in `docker-compose` (local) and ECS (prod); LocalStack emulates SQS identically; H2 used in both environments |
| **XI. Logs** | All services write structured logs to `stdout`/`stderr`; captured by CloudWatch Logs in production; visible via `docker-compose logs` locally |
| **XII. Admin processes** | H2 schema created via `ddl-auto=create` on startup; default admin user seeded by `DataInitializer.java` (ApplicationRunner); one-off tasks can be run as ECS `run-task` |
| **XIII. API first** | REST API fully documented in `docs/api-documentation.md`; contract defined before implementation |
| **XIV. Telemetry** | Spring Boot Actuator `/actuator/health` on all services (used by ECS health checks and docker-compose health checks); CloudWatch Logs for centralised observability |
| **XV. Auth & Authz** | JWT-based stateless authentication (HS256, 1-hour TTL); `ROLE_ADMIN` / `ROLE_USER` role-based access control; JWT secret stored in AWS SSM Parameter Store as SecureString |

---

## 7. Deployment Instructions

### Prerequisites

- AWS account with sufficient IAM permissions
- AWS CLI configured: `aws configure` (for initial Terraform bootstrap only)
- Terraform ≥ 1.5
- Docker Desktop running
- Java 21 + Maven

### First-time bootstrap (local Terraform apply)

The OIDC provider and GitHub Actions IAM role must be created once before GitHub Actions can take over.

```bash
# 1. Clone the infra repo
git clone https://github.com/SWE455-proj-team/infra
cd infra

# 2. Configure variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars: set jwt_secret, aws_region, account_id

# 3. Init and apply
terraform init
terraform apply
```

### Subsequent deploys (automated)

Push to `main` in any service repo — GitHub Actions handles build, push to ECR, and ECS redeploy automatically.

Push to `main` in the `infra` repo — Terraform applies any infrastructure changes automatically.

### First-time image push (after initial `terraform apply`)

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=us-east-1
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $REGISTRY

for SERVICE in eureka-server api-gateway user-service book-service; do
  docker build -t "${REGISTRY}/library-prod-${SERVICE}:latest" \
    https://github.com/SWE455-proj-team/${SERVICE}
  docker push "${REGISTRY}/library-prod-${SERVICE}:latest"
done

for SERVICE in eureka-server api-gateway user-service book-service; do
  aws ecs update-service \
    --cluster library-prod-cluster \
    --service library-prod-${SERVICE} \
    --force-new-deployment
done
```

---

## 8. Destroy and Restore Demo

This is the core demo: the entire cloud environment is destroyed and fully restored using only code.

### Destroy (~2 minutes)

Trigger the `infra-destroy.yml` workflow manually in GitHub Actions, or run locally:

```bash
cd infra
terraform destroy -auto-approve
```

This removes all AWS resources: ECS cluster, ALB, SQS queue, ECR repos, VPC, security groups, CloudWatch groups. The OIDC provider and GitHub Actions IAM role are **protected** by `lifecycle { prevent_destroy = true }` — they survive the destroy so the pipeline can re-deploy.

### Restore (~3 minutes infra + ~2 minutes ECS startup)

Trigger the `infra-apply-manual.yml` workflow in GitHub Actions, or run locally:

```bash
cd infra
terraform apply -auto-approve
```

Then push any commit to each service repository to trigger the service CI/CD pipelines, or manually trigger each `deploy-*.yml` workflow. The system returns to a fully working state without any AWS Console interaction.

**Total destroy → working: ~5 minutes.**

---

## 9. Local Development

```bash
# Build all JARs first (or let docker-compose build them)
mvn clean package -DskipTests   # from each service directory

# Start everything
docker-compose up --build

# Services available at:
# Eureka Dashboard:  http://localhost:8761
# API Gateway:       http://localhost:8080
# User Service:      http://localhost:8085
# Book Service:      http://localhost:8086
# LocalStack (SQS):  http://localhost:4566
```

Default admin credentials (seeded automatically): `admin / Admin123!`

---

## 10. Testing

All requests go through the API Gateway (port 8080 locally, ALB in production).

Production base URL: `http://library-prod-alb-2029213111.us-east-1.elb.amazonaws.com`

```bash
BASE=http://localhost:8080
# For production: BASE=http://library-prod-alb-2029213111.us-east-1.elb.amazonaws.com

# 1. Sign up a new user (auto-activated)
curl -X POST $BASE/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","firstName":"Alice","lastName":"Wonder","password":"pass"}'

# 2. Login as admin
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' | jq -r '.message')

# 3. Add a book (admin only)
curl -X POST $BASE/book/add \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"PrintedBook","ISBN":"1232","title":"Clean Code","author":"Martin","genre":"Technology","numOfPages":431,"hardcover":true}'

# 4. Add an audiobook
curl -X POST $BASE/book/add \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"AudioBook","ISBN":"2311","title":"Dune","author":"Herbert","genre":"SciFi","narrationLength":21}'

# 5. Add an e-book
curl -X POST $BASE/book/add \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"type":"EBook","ISBN":"3121","title":"The Pragmatic Programmer","author":"Thomas","genre":"Technology","fileFormat":"PDF"}'

# 6. List all books
curl -H "Authorization: Bearer $TOKEN" $BASE/book/all

# 7. Filter books
curl -H "Authorization: Bearer $TOKEN" "$BASE/book/filter?genre=Technology"

# 8. Test name-change SQS event propagation
curl -X POST $BASE/auth/change-name \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"<user-uuid>","firstName":"Jane","lastName":"Smith"}'
```

---

## 11. Known Limitations

- **`desired_count = 1`** — no horizontal scaling configured; add ECS auto-scaling for production load.
- **HTTP only** — no TLS termination on the ALB; add an ACM certificate and HTTPS listener for production.
- **H2 is ephemeral** — data is lost when a container restarts. This is intentional for the demo (enables fast destroy/restore). A production system would use RDS PostgreSQL with Flyway migrations.
- **Public subnets only** — ECS tasks are in public subnets with a public IP. A production system would use private subnets + NAT Gateway or VPC endpoints.
- **Eureka in ECS** — service discovery via Eureka works but AWS Cloud Map is a more native option.
- **Single-region** — no cross-region redundancy; acceptable for a university demo.

---

## 12. Source Code Repositories

| Repository | URL |
|---|---|
| `eureka-server` | https://github.com/SWE455-proj-team/eureka-server |
| `api-gateway` | https://github.com/SWE455-proj-team/api-gateway |
| `user-service` | https://github.com/SWE455-proj-team/user-service |
| `book-service` | https://github.com/SWE455-proj-team/book-service |
| `infra` (Terraform) | https://github.com/SWE455-proj-team/infra |

---

## 13. AI Prompt Appendix

AI (Claude Code, claude-sonnet-4-6) was used throughout this project. High-level prompts are recorded below and in the `prompts/` directory.

- **Prompt 01 — Initial project build:** [`../prompts/01-project-build.md`](../prompts/01-project-build.md)  
  Transform an existing Spring Cloud backend into a production-deployable AWS system with Terraform, GitHub Actions OIDC, SQS, Docker, and full 15-Factor compliance.

- **Prompt 02 — Cloud migration & fixes:** [`../prompts/02-cloud-migration-fixes.md`](../prompts/02-cloud-migration-fixes.md)  
  Migrate from RDS + NAT Gateway to H2 database for fast demo cycles; fix H2 reserved-keyword issues; seed default admin; fix Jackson deserialization; update IAM trust policy for multi-repo structure.
