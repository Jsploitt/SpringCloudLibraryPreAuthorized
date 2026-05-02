# ── ECS Cluster ───────────────────────────────────────────────────────────────
resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.common_tags
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

# ── Shared environment variables ──────────────────────────────────────────────
locals {
  db_host = aws_db_instance.main.address
  db_port = "3306"

  common_env = [
    { name = "AWS_REGION", value = var.aws_region },
    { name = "HIBERNATE_DDL_AUTO", value = "update" },
    { name = "HIBERNATE_ID_MAPPINGS", value = "false" },
    { name = "UUID_STORAGE_TYPE", value = "VARCHAR" },
    { name = "EUREKA_URL", value = "http://${aws_lb.main.dns_name}:8761/eureka" },
  ]

  db_env_user = [
    { name = "DB_HOST", value = local.db_host },
    { name = "DB_PORT", value = local.db_port },
    { name = "DB_USERNAME", value = var.db_username },
  ]
}

# ── Eureka Server Task Definition ─────────────────────────────────────────────
resource "aws_ecs_task_definition" "eureka_server" {
  family                   = "${local.name_prefix}-eureka-server"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "eureka-server"
    image     = "${aws_ecr_repository.eureka_server.repository_url}:${var.ecr_image_tag}"
    essential = true

    portMappings = [{ containerPort = 8761, protocol = "tcp" }]

    environment = concat(local.common_env, [
      { name = "SERVER_PORT", value = "8761" },
      { name = "EUREKA_REGISTER_WITH", value = "false" },
      { name = "EUREKA_FETCH_REGISTRY", value = "false" },
    ])

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.eureka_server.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -q --spider http://localhost:8761/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = local.common_tags
}

# ── User Service Task Definition ──────────────────────────────────────────────
resource "aws_ecs_task_definition" "user_service" {
  family                   = "${local.name_prefix}-user-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "user-service"
    image     = "${aws_ecr_repository.user_service.repository_url}:${var.ecr_image_tag}"
    essential = true

    portMappings = [{ containerPort = 8085, protocol = "tcp" }]

    environment = concat(local.common_env, local.db_env_user, [
      { name = "SERVER_PORT", value = "8085" },
      { name = "DB_NAME", value = "userdb" },
      { name = "USER_NAME_CHANGES_QUEUE_URL", value = aws_sqs_queue.user_name_changes.url },
    ])

    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
      { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.user_service.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -q --spider http://localhost:8085/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 90
    }
  }])

  tags = local.common_tags
}

# ── Book Service Task Definition ──────────────────────────────────────────────
resource "aws_ecs_task_definition" "book_service" {
  family                   = "${local.name_prefix}-book-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "book-service"
    image     = "${aws_ecr_repository.book_service.repository_url}:${var.ecr_image_tag}"
    essential = true

    portMappings = [{ containerPort = 8086, protocol = "tcp" }]

    environment = concat(local.common_env, local.db_env_user, [
      { name = "SERVER_PORT", value = "8086" },
      { name = "DB_NAME", value = "bookdb" },
      { name = "USER_NAME_CHANGES_QUEUE_URL", value = aws_sqs_queue.user_name_changes.url },
      { name = "USER_SERVICE_URL", value = "http://user-service" },
    ])

    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn },
      { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.book_service.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -q --spider http://localhost:8086/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 90
    }
  }])

  tags = local.common_tags
}

# ── API Gateway Task Definition ───────────────────────────────────────────────
resource "aws_ecs_task_definition" "api_gateway" {
  family                   = "${local.name_prefix}-api-gateway"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name      = "api-gateway"
    image     = "${aws_ecr_repository.api_gateway.repository_url}:${var.ecr_image_tag}"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = concat(local.common_env, [
      { name = "SERVER_PORT", value = "8080" },
    ])

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api_gateway.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }

    healthCheck = {
      command     = ["CMD-SHELL", "wget -q --spider http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 10
      retries     = 3
      startPeriod = 60
    }
  }])

  tags = local.common_tags
}

# ── ECS Services ──────────────────────────────────────────────────────────────
resource "aws_ecs_service" "eureka_server" {
  name            = "eureka-server"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.eureka_server.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  force_new_deployment = true

  tags = local.common_tags
}

resource "aws_ecs_service" "user_service" {
  name            = "user-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.user_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  force_new_deployment = true
  depends_on           = [aws_ecs_service.eureka_server]

  tags = local.common_tags
}

resource "aws_ecs_service" "book_service" {
  name            = "book-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.book_service.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  force_new_deployment = true
  depends_on           = [aws_ecs_service.eureka_server]

  tags = local.common_tags
}

resource "aws_ecs_service" "api_gateway" {
  name            = "api-gateway"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api_gateway.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api_gateway.arn
    container_name   = "api-gateway"
    container_port   = 8080
  }

  force_new_deployment = true
  depends_on           = [aws_lb_listener.http, aws_ecs_service.eureka_server]

  tags = local.common_tags
}
