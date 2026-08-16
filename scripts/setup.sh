#!/usr/bin/env bash
# =============================================================================
# setup.sh — 生成部署环境配置（.env + 本地配置模板），幂等
#
# 动作：
#   1. 若 deploy/.env 不存在，从 .env.example 复制并生成 JWT 密钥与 Redis 密码
#   2. 准备 backend 本地配置模板 /etc/guicang/application-local.yml（容器挂载）
# 用法: bash scripts/setup.sh [--force]
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$REPO_ROOT/deploy/.env"
LOCAL_CONFIG_DIR="/etc/guicang"

[ "${1:-}" = "--force" ] && FORCE=1 || FORCE=0

say() { printf '[setup] %s\n' "$*"; }

gen_secret() { openssl rand -hex 32; }
gen_password() { openssl rand -base64 18 | tr -d '/+='; }

# ---- .env ----
if [ -f "$ENV_FILE" ] && [ "$FORCE" -ne 1 ]; then
  say ".env 已存在，跳过（--force 可重新生成）"
else
  cp "$REPO_ROOT/deploy/.env.example" "$ENV_FILE"
  sed -i "s|^GUICANG_JWT_SECRET=$|GUICANG_JWT_SECRET=$(gen_secret)|" "$ENV_FILE"
  sed -i "s|^GUICANG_REDIS_PASSWORD=$|GUICANG_REDIS_PASSWORD=$(gen_password)|" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  say "已生成 $ENV_FILE（权限 600）"
fi

# ---- 本地配置模板（容器挂载 /etc/guicang，覆盖默认密钥） ----
if [ -d "$LOCAL_CONFIG_DIR" ]; then
  cat > "$LOCAL_CONFIG_DIR/application-local.yml" <<EOF
# GuiCang 本地配置（setup.sh 生成，权限 600；敏感值不进 git）
guicang:
  jwt:
    secret: $(grep '^GUICANG_JWT_SECRET=' "$ENV_FILE" | cut -d= -f2)
EOF
  chmod 600 "$LOCAL_CONFIG_DIR/application-local.yml"
  say "已写入 $LOCAL_CONFIG_DIR/application-local.yml（容器挂载后覆盖默认密钥）"
else
  say "跳过：$LOCAL_CONFIG_DIR 不存在（容器部署时由 compose 卷自动创建）"
fi

say "完成。下一步：bash scripts/deploy.sh"
