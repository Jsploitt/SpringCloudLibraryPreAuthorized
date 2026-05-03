# ── Application Load Balancer ─────────────────────────────────────────────────
resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = merge(local.common_tags, { Name = "${local.name_prefix}-alb" })
}

# ── Target Group → API Gateway ────────────────────────────────────────────────
resource "aws_lb_target_group" "api_gateway" {
  name        = "${local.name_prefix}-tg-api-gateway"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 5
    matcher             = "200"
  }

  tags = local.common_tags
}

# ── Target Group → Eureka Server ─────────────────────────────────────────────
resource "aws_lb_target_group" "eureka_server" {
  name        = "${local.name_prefix}-tg-eureka"
  port        = 8761
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }

  tags = local.common_tags
}

# ── HTTP Listener (port 80 → api-gateway) ────────────────────────────────────
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api_gateway.arn
  }
}

# ── Eureka Listener (port 8761 → eureka-server) ───────────────────────────────
resource "aws_lb_listener" "eureka" {
  load_balancer_arn = aws_lb.main.arn
  port              = 8761
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.eureka_server.arn
  }
}

# ── Path-based listener rules ─────────────────────────────────────────────────
# /auth/* and /book/* are both forwarded to the api-gateway target group.
# The API Gateway container internally routes them to user-service/book-service via Eureka.

resource "aws_lb_listener_rule" "auth" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api_gateway.arn
  }

  condition {
    path_pattern { values = ["/auth/*"] }
  }
}

resource "aws_lb_listener_rule" "books" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 20

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api_gateway.arn
  }

  condition {
    path_pattern { values = ["/book/*"] }
  }
}
