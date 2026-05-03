#!/usr/bin/env bash
# ── Demo: destroy app layer and restore ──────────────────────────────────────
# Run from repo root. RDS is kept alive to save ~10 minutes.
# Total expected time: destroy ~3 min, restore ~5 min + CI/CD ~8 min

set -e
cd "$(dirname "$0")/infra"

echo "=== DESTROYING app layer ==="
terraform destroy -auto-approve \
  -target=aws_ecs_service.api_gateway \
  -target=aws_ecs_service.user_service \
  -target=aws_ecs_service.book_service \
  -target=aws_ecs_service.eureka_server \
  -target=aws_ecs_cluster.main \
  -target=aws_ecs_cluster_capacity_providers.main \
  -target=aws_lb.main \
  -target=aws_lb_listener.http \
  -target=aws_lb_listener.eureka \
  -target=aws_lb_listener_rule.auth \
  -target=aws_lb_listener_rule.books \
  -target=aws_lb_target_group.api_gateway \
  -target=aws_lb_target_group.eureka_server \
  -target=aws_ecr_repository.api_gateway \
  -target=aws_ecr_repository.user_service \
  -target=aws_ecr_repository.book_service \
  -target=aws_ecr_repository.eureka_server

echo ""
echo "=== RESTORING infra ==="
terraform apply -auto-approve

echo ""
echo "=== Push a commit to trigger CI/CD image build & deploy ==="
echo "Run: git commit --allow-empty -m 'demo: restore' && git push"
