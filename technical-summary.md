Now I have everything. Here's the full technical summary:

---

# SpringCloudLibraryPreAuthorized — Complete Technical Summary

---

## 1. What This System Is

A **digital library management system** built as a set of Spring Boot microservices. Users can sign up, log in, and browse/add books. The system is fully deployed on AWS using Terraform-managed infrastructure and a GitHub Actions CI/CD pipeline.

---

## 2. Application Architecture

### Services (4 total)

```
Internet
   │
   ▼
ALB (port 80)
   │
   ▼
API Gateway  (:8080)  ◄── All traffic enters here
   │          │
   │          └── /auth/**  ──► User Service  (:8085)
   │          └── /book/**  ──► Book Service  (:8086)
   │
   └── All services register with ──► Eureka Server (:8761)
```

#### Eureka Server
- Service registry. Every other service registers itself on startup.
- The API Gateway resolves `book-service` and `user-service` by name through Eureka (no hardcoded IPs needed).
- Standalone mode — does not register itself.

#### API Gateway
- Built with **Spring Cloud Gateway MVC** (not reactive/WebFlux).
- Two routes defined in `SpringCloudConfig.java`:
  - `GET/POST /book/**` → load-balanced to `book-service`
  - `GET/POST /auth/**` → load-balanced to `user-service`
- The `@LoadBalanced` RestTemplate resolves service names via Eureka.
- Acts as the single entry point — clients never talk to individual services directly.

#### User Service (:8085)
Handles authentication and user management. Key endpoints under `/auth/`:

| Endpoint | Role Required | Description |
|---|---|---|
| `POST /auth/signup` | Public | Register new user (ROLE_USER, ACTIVE) |
| `POST /auth/login` | Public | Returns a JWT token |
| `GET /auth/user/{id}` | Any authenticated | Get user details by UUID |
| `GET /auth/status/{id}` | Any authenticated | Get user status |
| `POST /auth/change-status` | ROLE_ADMIN | Activate/deactivate a user |
| `POST /auth/change-name` | ROLE_ADMIN | Change a user's name (publishes SQS event) |

- New users start as `ROLE_USER` + `ACTIVE` status by default.
- A default `admin/Admin123!` user is seeded at startup by `DataInitializer.java`.
- Passwords are BCrypt-encoded before storage.
- JWT secret loaded from AWS SSM Parameter Store at runtime.

#### Book Service (:8086)
Handles book catalog management. Endpoints under `/book/`:

| Endpoint | Role Required | Description |
|---|---|---|
| `POST /book/add` | ROLE_ADMIN | Add a book (validates ISBN, fetches creator name from User Service) |
| `GET /book/all` | ROLE_USER | List all books |
| `GET /book/{ISBN}` | ROLE_USER | Get book by ISBN |
| `DELETE /book/{ISBN}` | ROLE_ADMIN | Delete a book |
| `GET /book/filter?genre=&author=` | ROLE_USER | Filter books |
| `GET /book/total` | ROLE_USER | Get total count |

- Every book endpoint first calls User Service to verify the user's `ACTIVE` status before proceeding.
- On `POST /book/add`, it also calls User Service to fetch the creator's `firstName`/`lastName` and stores them on the book.
- Uses `@LoadBalanced` RestTemplate with Eureka to call `http://user-service/auth/...`.

---

## 3. Book Data Model

Books use **JPA JOINED inheritance** + **Jackson polymorphism**:

```
Book (abstract, @Entity)
  ├── ISBN (@Id, 4-digit custom format)
  ├── title, author, genre
  ├── refCode (auto-generated: first 2 chars of author + genre)
  ├── creatorId, creatorFirstName, creatorLastName
  │
  └── WrittenBook (abstract)
        ├── numOfPages
        │
        ├── PrintedBook  ── hardcover (boolean)
        └── EBook        ── (e-book specific fields)

AudioBook (extends Book directly)
```

- `bookType` field in JSON is the discriminator: `"PrintedBook"`, `"AudioBook"`, or `"EBook"`.
- Custom ISBN: exactly 4 digits, checksum formula: `(d1×3 + d2×2 + d3×1) % 4 == d4`.
  - Example valid ISBN: `"1232"` → `(1×3 + 2×2 + 3×1) % 4 = 2 ✓`

---

## 4. Authentication (JWT)

- JWT tokens are issued by User Service on login, validated by both User Service and Book Service independently (both hold the same secret).
- Token payload includes: `role`, `username`, `sub` (UUID), `iat`, `exp`.
- Expiry: 1 hour.
- Book Service extracts the UUID from the token to look up the user in User Service.
- Secret is stored in **AWS SSM Parameter Store** (`/library-prod/jwt-secret`) and injected as `JWT_SECRET` env var at ECS task launch.

---

## 5. Async Messaging (SQS)

- When an admin changes a user's name (`POST /auth/change-name`), User Service publishes a `UserNameChangedEvent` to an **AWS SQS queue**.
- Book Service consumes messages from the queue to update the `creatorFirstName`/`creatorLastName` on any books that user created.
- Queue name: `library-prod-user-name-changes`
- Long polling (20s wait), 1-day message retention.

---

## 6. Database

Both services use **H2 databases** (no external DB):

| Service | DB URL |
|---|---|
| User Service | `jdbc:h2:mem:userdb` |
| Book Service | `jdbc:h2:mem:bookdb` |

- `ddl-auto=create` — schema is created fresh on every startup.
- Data is lost when the container restarts (intentional for fast demo cycles).
- The `User` entity uses `@Table(name="users")` to avoid H2's reserved keyword `USER`.

---

## 7. AWS Infrastructure (Terraform)

All infrastructure is defined in `infra/` and managed with Terraform. State is stored **locally** (`terraform.tfstate`).

### Resources Created

#### Networking (`vpc.tf`)
- **VPC**: `10.0.0.0/16`
- **2 Public Subnets**: `10.0.1.0/24`, `10.0.2.0/24` across `us-east-1a`, `us-east-1b`
- **Internet Gateway** + public route table
- No NAT Gateway, no private subnets — all ECS tasks run in public subnets with `assign_public_ip = true`

#### Load Balancer (`alb.tf`)
- **Internet-facing ALB** across both public subnets
- **Port 80 listener**: forwards to API Gateway (port 8080)
- **Port 8761 listener**: forwards to Eureka Server (direct access for debugging)
- Path-based rules: `/auth/*` and `/book/*` both route to API Gateway target group

#### Container Registry (`ecr.tf`)
- 4 ECR repositories: `library-prod/eureka-server`, `library-prod/api-gateway`, `library-prod/user-service`, `library-prod/book-service`
- Lifecycle policy: keeps 10 most recent images, deletes older ones

#### Compute (`ecs.tf`)
- **ECS Cluster**: `library-prod-cluster` with Container Insights enabled
- **4 ECS Fargate services** (1 task each):

| Service | CPU | Memory | Port |
|---|---|---|---|
| eureka-server | 512 | 1024 MB | 8761 |
| user-service | 512 | 1024 MB | 8085 |
| book-service | 512 | 1024 MB | 8086 |
| api-gateway | 256 | 512 MB | 8080 |

- All tasks run in public subnets with public IPs (no NAT needed)
- `user-service` and `book-service` depend on Eureka starting first
- Deployment: `minimum_healthy_percent = 0`, `maximum_percent = 100` (allows rolling replace with 1 task)

#### Secrets (`rds.tf` / SSM)
- `aws_ssm_parameter.jwt_secret` — stores the JWT secret as a `SecureString`
- ECS task execution role has `ssm:GetParameters` permission to read it at launch

#### Messaging (`sqs.tf`)
- 1 SQS queue: `library-prod-user-name-changes`
- ECS task role has `sqs:SendMessage`, `sqs:ReceiveMessage`, `sqs:DeleteMessage` permissions

#### Security Groups (`security_groups.tf`)
- **ALB SG**: inbound HTTP (80) + Eureka (8761) from `0.0.0.0/0`
- **ECS SG**: inbound ports 8080–8086 from ALB SG + inbound from VPC CIDR (service-to-service), full egress

#### IAM (`iam.tf`)
- **ECS Task Execution Role**: pulls ECR images, writes CloudWatch logs, reads SSM secrets
- **ECS Task Role**: SQS access at runtime
- **GitHub Actions OIDC Role**: allows GitHub Actions to assume an AWS role without long-lived credentials (`AdministratorAccess`)
- Both OIDC provider and GitHub Actions role have `lifecycle { prevent_destroy = true }` — they survive `terraform destroy`

#### Monitoring (`cloudwatch.tf`)
- CloudWatch log groups for each service (logs accessible in AWS console)

---

## 8. CI/CD Pipeline (GitHub Actions)

File: `.github/workflows/deploy.yml`  
Triggered on: `push` to `main` branch, or manual dispatch.

```
Push to main
     │
     ▼
1. Checkout code
2. Setup Java 21 (Temurin) + Maven cache
3. mvn clean package -DskipTests  (build all 4 JARs)
4. Configure AWS credentials via OIDC
     └── Assumes role: secrets.AWS_ROLE_ARN
         (No long-lived AWS keys stored in GitHub)
5. Login to ECR
6. Build & push Docker images × 4
     ├── Tag: <git-sha>
     └── Tag: latest
7. Force ECS redeployment × 4 services
     └── aws ecs update-service --force-new-deployment
8. Pipeline summary
```

- **No Terraform in the pipeline** — infra is applied locally. Only app deployments go through CI/CD.
- Image tags use the Git SHA for traceability plus `latest` for ECS task definition compatibility.

---

## 9. Demo Flow (Destroy → Restore)

The `demo.sh` script demonstrates infrastructure-as-code resilience:

```bash
terraform destroy   # ~2 min  (OIDC + IAM role survive due to prevent_destroy)
terraform apply     # ~3 min  (recreates VPC, ALB, ECS, SQS, ECR, SSM)
git push            # triggers CI/CD → builds images → deploys to new ECS
```

Total: **~5–7 minutes** from zero to fully running system.

---

## 10. Request Flow (End-to-End Example)

```
curl POST /book/add  (with JWT)
   │
   ▼
ALB  ──port 80──► API Gateway container
                      │
                      │  Route: /book/**
                      │  Filter: lb("book-service")  ← Eureka lookup
                      ▼
                  Book Service
                      │
                      ├─ JWT validation (local)
                      ├─ @PreAuthorize("hasRole('ADMIN')")
                      ├─ getUserStatus()  ──► RestTemplate lb("user-service") ──► User Service
                      ├─ getCreatorInfo() ──► RestTemplate lb("user-service") ──► User Service
                      ├─ verifyISBN()
                      └─ bookService.addBook() ──► H2 DB
```

---

## 11. Key Design Decisions

| Decision | Reason |
|---|---|
| H2 DB (no RDS) | Eliminates 5+ min RDS provision/destroy time for demo |
| No NAT Gateway | Eliminates 2–3 min destroy time + ongoing cost; public subnets used instead |
| Terraform run locally | Avoids state management complexity; single developer owns infra |
| OIDC for GitHub Actions | No long-lived AWS credentials stored as secrets |
| `prevent_destroy` on OIDC + IAM role | These are bootstrap resources; recreating them breaks GitHub Actions |
| `ddl-auto=create` | H2 schema must be created fresh on every cold start |
| `@Table(name="users")` | H2 reserved keyword `USER` cannot be used as a table name |

---

## 12. Current Live Endpoint

```
http://library-prod-alb-2029213111.us-east-1.elb.amazonaws.com
```