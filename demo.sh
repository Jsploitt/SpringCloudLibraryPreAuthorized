#!/usr/bin/env bash
# ── Demo: full destroy and restore (~5-7 min total) ──────────────────────────
# Run from repo root.
set -e
cd "$(dirname "$0")/infra"

echo "=== [1/3] DESTROYING entire environment ==="
terraform destroy -auto-approve

echo ""
echo "=== [2/3] RESTORING infrastructure ==="
terraform apply -auto-approve

echo ""
echo "=== [3/3] Trigger CI/CD to build & deploy images ==="
cd ..
git commit --allow-empty -m "demo: trigger redeploy" && git push

echo ""
echo "Done! Monitor pipeline at: https://github.com/Jsploitt/SpringCloudLibraryPreAuthorized/actions"
