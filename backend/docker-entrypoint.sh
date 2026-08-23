#!/usr/bin/env bash
# =============================================================================
# GuiCang 后端容器入口（轻量/local 模式）
#
# 轻量模式（docker-compose.lite.yml，GUICANG_AUTH_VERIFIER=local）下，
# 认证走容器内 guicang-pam-verify.py（直调 libpam），需要能读 /etc/shadow。
# 因此本入口在容器内以 root 创建/更新一个管理员系统账号，再启动后端。
# 这是「隔离的容器内账号」：不读取、不挂载宿主机 /etc/shadow，凭据互不影响。
#
# 环境变量：
#   GUICANG_CONTAINER_ADMIN_USER     容器内管理员用户名（默认 admin）
#   GUICANG_CONTAINER_ADMIN_PASSWORD 容器内管理员密码（默认 guicang123456）
#   GUICANG_CONTAINER_ADMIN_UID      容器内管理员 uid（默认 1000）
#
# 生产模式（docker-compose.yml，GUICANG_AUTH_VERIFIER=helper）不设
# GUICANG_CONTAINER_ADMIN_USER，本入口直接启动后端，走宿主机 helper。
# =============================================================================
set -uo pipefail

ADMIN_USER="${GUICANG_CONTAINER_ADMIN_USER:-}"
ADMIN_PASS="${GUICANG_CONTAINER_ADMIN_PASSWORD:-guicang123456}"
ADMIN_UID="${GUICANG_CONTAINER_ADMIN_UID:-1000}"

if [ -n "$ADMIN_USER" ]; then
  echo "[entrypoint] 创建容器内管理员系统账号: $ADMIN_USER (uid=$ADMIN_UID)"

  # 创建用户组（幂等：已存在则复用；GID 冲突则自动分配）
  if ! getent group "$ADMIN_USER" >/dev/null 2>&1; then
    if getent group "$ADMIN_UID" >/dev/null 2>&1; then
      groupadd "$ADMIN_USER" >/dev/null 2>&1 || echo "[entrypoint] 组创建失败，继续"
    else
      groupadd -g "$ADMIN_UID" "$ADMIN_USER" >/dev/null 2>&1 || echo "[entrypoint] 组创建失败，继续"
    fi
  fi

  # 创建用户（幂等：已存在则跳过；uid 冲突则自动分配）
  if ! id "$ADMIN_USER" >/dev/null 2>&1; then
    if getent passwd "$ADMIN_UID" >/dev/null 2>&1; then
      useradd -m -g "$ADMIN_USER" -s /bin/bash "$ADMIN_USER" >/dev/null 2>&1 \
        || echo "[entrypoint] 用户创建失败，继续"
    else
      useradd -m -u "$ADMIN_UID" -g "$ADMIN_USER" -s /bin/bash "$ADMIN_USER" >/dev/null 2>&1 \
        || echo "[entrypoint] 用户创建失败，继续"
    fi
  fi

  # 设置/更新密码（root 可写容器内 shadow；失败不阻断启动，仅提示）
  if ! echo "$ADMIN_USER:$ADMIN_PASS" | chpasswd; then
    echo "[entrypoint] 密码设置失败，继续（可能影响登录）"
  fi
  echo "[entrypoint] 管理员账号就绪（容器内，仅本容器认证）"
fi

# 转交 Spring Boot 启动（生产 helper 模式保持原有非 root 语义）
exec java -jar app.jar "$@"
