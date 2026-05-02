locals {
  services = ["eureka-server", "api-gateway", "user-service", "book-service"]
}

resource "aws_ecr_repository" "eureka_server" {
  name                 = "${local.name_prefix}/eureka-server"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, { Name = "eureka-server" })
}

resource "aws_ecr_repository" "api_gateway" {
  name                 = "${local.name_prefix}/api-gateway"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, { Name = "api-gateway" })
}

resource "aws_ecr_repository" "user_service" {
  name                 = "${local.name_prefix}/user-service"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, { Name = "user-service" })
}

resource "aws_ecr_repository" "book_service" {
  name                 = "${local.name_prefix}/book-service"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, { Name = "book-service" })
}

# Lifecycle policy: keep only the 10 most recent images per repo
resource "aws_ecr_lifecycle_policy" "cleanup" {
  for_each   = toset(local.services)
  repository = "${local.name_prefix}/${each.key}"

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })

  depends_on = [
    aws_ecr_repository.eureka_server,
    aws_ecr_repository.api_gateway,
    aws_ecr_repository.user_service,
    aws_ecr_repository.book_service,
  ]
}
