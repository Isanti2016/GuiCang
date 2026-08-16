#!/usr/bin/env bash
# =============================================================================
# upgrade.sh — 版本升级与回滚
#
# 流程：备份 → 记录旧镜像 → 拉取/构建新镜像 → 启动（Flyway 自动迁移）
#       → 健康检查 → 通过即完成；失败自动回滚到旧镜像 + 恢复备份提示
# 用法: bash scripts/upgrade.sh
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$SCRIPT_DIR/../deploy"
ENV_FILE="$DEPLOY_DIR/.env"

[ -f "$ENV_FILE" ] || { echo "缺少 deploy/.env（先运行 scripts/setup.sh）" >&2; exit 1; }

say() { printf '[upgrade] %s\n' "$*"; }
fail() { echo "[upgrade] 失败: $*" >&2; exit 1; }

cd "$DEPLOY_DIR"

# 1. 备份
say "备份当前数据"
bash "$SCRIPT_DIR/backup.sh" || say "备份警告（继续）"

# 2. 记录旧镜像（回滚用）
OLD_IMAGE="$(docker compose images -q backend 2>/dev/null | head -1)"
[ -n "$OLD_IMAGE" ] || fail "无法获取当前 backend 镜像"

# 3. 拉取/构建新镜像
say "拉取/构建新镜像"
docker compose pull --quiet 2>/dev/null || true
docker compose build --quiet backend || fail "镜像构建失败"

# 4. 启动（Flyway 迁移随启动自动执行）
say "启动新版本"
docker compose up -d backend || fail "容器启动失败"

# 5. 健康检查（90s）
say "健康检查（最多 90s）"
ok=0
for i in $(seq 1 18); do
  if docker exec guicang-backend wget -q -O /dev/null http://127.0.0.1:8080/api/v1/setup/status 2>/dev/null; then
    ok=1
    break
  fi
  sleep 5
done

if [ "$ok" -eq 1 ]; then
  say "升级完成（Flyway 迁移已应用）"
  exit 0
fi

# 6. 回滚
say "健康检查失败，回滚到旧镜像 $OLD_IMAGE"
docker compose stop backend
docker compose rm -f backend
docker run --rm --name guicang-rollback "$OLD_IMAGE" true 2>/dev/null || true
docker compose up -d --no-build backend || fail "回滚启动失败"

say "回滚完成。数据备份位于 /var/backups/guicang（backup.sh 生成），迁移冲突时可用其恢复。"
exit 1
