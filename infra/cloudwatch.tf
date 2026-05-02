resource "aws_cloudwatch_log_group" "eureka_server" {
  name              = "/ecs/${local.name_prefix}/eureka-server"
  retention_in_days = 7
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/ecs/${local.name_prefix}/api-gateway"
  retention_in_days = 7
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "user_service" {
  name              = "/ecs/${local.name_prefix}/user-service"
  retention_in_days = 7
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "book_service" {
  name              = "/ecs/${local.name_prefix}/book-service"
  retention_in_days = 7
  tags              = local.common_tags
}
