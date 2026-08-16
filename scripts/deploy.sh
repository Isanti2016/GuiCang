#!/usr/bin/env bash
# =============================================================================
# deploy.sh — 一键部署（docker compose 构建启动 + 健康检查 + 前端可用性探测）
#
# 前置：1) bash scripts/setup.sh 已生成 .env
#        2) 宿主已部署 guicang-helper（scripts/install-helper.sh，权限/账号供给）
#        3) 已执行目录权限脚本（scripts/dir-permissions.sh --apply，可选）
# 用法: bash scripts/deploy.sh
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$SCRIPT_DIR/../deploy"
ENV_FILE="$DEPLOY_DIR/.env"

[ -f "$ENV_FILE" ] || { echo "缺少 deploy/.env，请先运行 scripts/setup.sh" >&2; exit 1; }
command -v docker >/dev/null || { echo "缺少 docker" >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "缺少 docker compose 插件" >&2; exit 1; }

say() { printf '[deploy] %s\n' "$*"; }

cd "$DEPLOY_DIR"

say "构建并启动容器"
docker compose up -d --build

say "等待 backend 健康就绪（最多 90s）"
for i in $(seq 1 18); do
  if docker exec guicang-backend wget -q -O /dev/null http://127.0.0.1:8080/api/v1/setup/status 2>/dev/null; then
    break
  fi
  sleep 5
done

say "探测前端（:80）与后端（经 Nginx 反代）"
curl -fsS -o /dev/null http://127.0.0.1/ && say "前端 OK"
curl -fsS -o /dev/null "http://127.0.0.1/api/v1/setup/status" && say "API OK"

say "容器状态："
docker compose ps

say "完成。首次使用请访问 http://<内网IP>/ 完成初始化向导（创建 admin）"
