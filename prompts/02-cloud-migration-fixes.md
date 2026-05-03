# AI Prompt 02 — Cloud Migration & Production Fixes

**Tool:** Claude Code (claude-sonnet-4-6)  
**Date:** 2026-05-02 / 2026-05-03  
**Purpose:** Migrate from RDS + NAT Gateway to H2 in-memory database for a fast destroy/restore demo; fix production bugs discovered during live deployment; update IAM for multi-repo CI/CD structure.

---

## Prompt

We have a Spring Cloud Library backend running on AWS ECS Fargate, built in the previous session. Several issues were discovered during live deployment and demo preparation. Fix all of the following:

### 1. Replace RDS with H2 (fast demo requirement)

The demo requires destroying and restoring the entire cloud environment in under 5 minutes. RDS takes 8-10 minutes to create and costs money continuously.

Replace MariaDB/RDS with H2 in-memory database for both user-service and book-service:
- Add H2 dependency to each service's pom.xml.
- Update application.properties to use H2 dialect and `ddl-auto=create`.
- Remove RDS-related Terraform resources (aws_db_instance, aws_db_subnet_group, private subnets, NAT gateway).
- Remove DB env vars (DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD) from ECS task definitions and docker-compose.
- Remove SSM parameter for DB_PASSWORD.
- Update all IAM policies and security groups accordingly.

### 2. Fix H2 reserved keyword conflict

The `user` table name conflicts with H2's reserved SQL keyword.

Add `@Table(name = "users")` to the User entity. Remove `spring.jpa.properties.hibernate.globally_quoted_identifiers=true` which was a previous workaround.

### 3. Seed default admin user

Currently, the first user must be manually promoted to ROLE_ADMIN via a DB update — impossible with H2 (no persistent storage) and against the 15-factor methodology.

Create a `DataInitializer.java` ApplicationRunner in user-service that seeds an admin account on startup if it does not exist:
- username: `admin`
- email: `admin@library.com`
- password: `Admin123!` (BCrypt-encoded)
- role: `ROLE_ADMIN`
- status: `ACTIVE`

Also change default signup status from `INACTIVE` to `ACTIVE` so new users can use the API immediately without needing admin approval.

### 4. Fix Jackson ISBN deserialization

The field `private String ISBN` in the Book entity is not being deserialized correctly from JSON because Jackson's naming convention for `getISBN()` generates an ambiguous property name.

Add `@JsonProperty("ISBN")` to the `ISBN` field in the Book base class.

### 5. Update IAM trust policy for multi-repo structure

The GitHub Actions OIDC role currently only trusts `repo:Jsploitt/SpringCloudLibraryPreAuthorized:*`. The project has been split into 5 separate repositories under the `SWE455-proj-team` GitHub organisation.

Update the `aws_iam_role.github_actions` trust policy in `infra/iam.tf` to include all 5 repos:
- `repo:SWE455-proj-team/user-service:*`
- `repo:SWE455-proj-team/book-service:*`
- `repo:SWE455-proj-team/api-gateway:*`
- `repo:SWE455-proj-team/eureka-server:*`
- `repo:SWE455-proj-team/infra:*`

Keep the original `Jsploitt/SpringCloudLibraryPreAuthorized` entry for backward compatibility during the transition.

### 6. Update docker-compose for H2

Remove MariaDB container and all database-related environment variables from docker-compose.yml. Keep LocalStack for SQS. Ensure all four services start correctly with H2.

### 7. Create .env.example

Document all environment variables used across the system in `.env.example` at the project root, with comments explaining each variable's purpose, local vs. production values, and how production values are injected (SSM, ECS task definition).

---

## Key Decisions Made by the AI

| Decision | Rationale |
|---|---|
| H2 `ddl-auto=create` instead of `update` | H2 starts empty on each container start; `update` doesn't create tables from scratch |
| `@Table(name="users")` | H2 treats `USER` as a reserved keyword; renaming the table is cleaner than quoting everywhere |
| `DataInitializer` as `ApplicationRunner` | Runs after Spring context is fully initialized; idempotent (checks before seeding) |
| Default signup status `ACTIVE` | Avoids chicken-and-egg: need admin to activate users, but admin doesn't exist yet |
| `@JsonProperty("ISBN")` on field | Jackson derives property name `ISBN` inconsistently from `getISBN()` getter — explicit annotation is authoritative |
| Keep old repo in OIDC trust list | Smooth transition period; new repos can deploy immediately without losing the old pipeline |
| Public subnets, no NAT | Eliminates NAT Gateway cost (~$32/month) and 3-minute creation time; ECS tasks reach ECR/SQS via Internet Gateway |
