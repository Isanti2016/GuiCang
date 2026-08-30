#!/usr/bin/env bash
# =============================================================================
# docker-start.sh — GuiCang 一键启动脚本（Docker 打包）
#
# 用法：
#   bash scripts/docker-start.sh [start]   一键构建并启动（默认命令）
#   bash scripts/docker-start.sh stop      停止容器（保留数据卷）
#   bash scripts/docker-start.sh restart   重启容器
#   bash scripts/docker-start.sh status    查看状态
#   bash scripts/docker-start.sh logs      查看后端日志（-f 跟随可加参数）
#   bash scripts/docker-start.sh down      停止并移除容器/网络（保留数据卷）
#   bash scripts/docker-start.sh full      完整模式：宿主机 helper 认证（生产）
#   bash scripts/docker-start.sh help      帮助
#
# 首次运行自动：
#   1. 生成 deploy/.env（JWT 密钥等，幂等；--force 重新生成）
#   2. docker compose 构建镜像并后台启动（nginx + backend）
#   3. 健康检查：等待后端就绪、探测前端与 API
#
# 模式说明：
#   - 默认（轻量）：容器内自建 admin 系统账号，local 认证，开箱即用；不接触宿主凭据。
#     登录：admin / 密码由 .env 的 GUICANG_CONTAINER_ADMIN_PASSWORD 决定（默认 guicang123456）
#   - --full（生产）：走宿主机 guicang-helper 非 root 认证，见 deploy.sh
#
# 前置：docker 与 docker compose 插件已安装
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="$REPO_ROOT/deploy"
ENV_FILE="$DEPLOY_DIR/.env"
LITE_COMPOSE="$DEPLOY_DIR/docker-compose.lite.yml"
FULL_COMPOSE="$DEPLOY_DIR/docker-compose.yml"

say() { printf '[docker-start] %s\n' "$*"; }
die() { printf '[docker-start] 错误: %s\n' "$*" >&2; exit 1; }

# ---------- 前置检查 ----------
command -v docker >/dev/null 2>&1 || die "未找到 docker，请先安装"
docker compose version >/dev/null 2>&1 || die "未找到 docker compose 插件"

# ---------- 环境准备 ----------
gen_secret() { openssl rand -hex 32; }
gen_password() { openssl rand -base64 18 | tr -d '/+='; }

ensure_env() {
  # 补齐 .env 缺失的必需项（幂等；不覆盖已存在的值）
  ensure_field() {
    local key="$1" gen="$2"
    if ! grep -qE "^${key}=.+" "$ENV_FILE"; then
      local val
      val="$($gen)"
      printf '%s=%s\n' "$key" "$val" >> "$ENV_FILE"
      say "已补充 ${key}"
    fi
  }
  if [ -f "$ENV_FILE" ]; then
    say ".env 已存在，检查并补齐缺失字段"
  else
    say "生成 deploy/.env（JWT 密钥 + 容器管理员密码）"
    cp "$DEPLOY_DIR/.env.example" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
  fi
  ensure_field GUICANG_JWT_SECRET gen_secret
  ensure_field GUICANG_REDIS_PASSWORD gen_password
  ensure_field GUICANG_CONTAINER_ADMIN_PASSWORD gen_password
  # 默认值字段（未显式配置时补默认）
  if ! grep -qE '^GUICANG_CONTAINER_ADMIN_USER=' "$ENV_FILE"; then
    printf '%s\n' 'GUICANG_CONTAINER_ADMIN_USER=admin' >> "$ENV_FILE"
  fi
  if ! grep -qE '^GUICANG_HTTP_PORT=' "$ENV_FILE"; then
    printf '%s\n' 'GUICANG_HTTP_PORT=80' >> "$ENV_FILE"
  fi
  if ! grep -qE '^GUICANG_STORAGE_ROOT=' "$ENV_FILE"; then
    printf '%s\n' 'GUICANG_STORAGE_ROOT=/home/wb/nas' >> "$ENV_FILE"
  fi
  say ".env 就绪（权限 600）"
}

# ---------- 健康检查 ----------
wait_healthy() {
  say "等待后端就绪（最多 90s）…"
  for _ in $(seq 1 18); do
    if curl -fsS -o /dev/null "http://127.0.0.1:${HTTP_PORT}/api/v1/setup/status" 2>/dev/null; then
      say "后端已就绪"
      return 0
    fi
    sleep 5
  done
  say "警告：后端 90s 内未就绪，请用 'docker-start.sh logs' 查看日志"
  return 1
}

# ---------- 子命令 ----------
cmd_start() {
  ensure_env
  # 读取 HTTP 端口（默认 80；80 被占用时提示用 .env 的 GUICANG_HTTP_PORT）
  HTTP_PORT="$(grep -E '^GUICANG_HTTP_PORT=' "$ENV_FILE" 2>/dev/null | cut -d= -f2 || echo 80)"
  [ -n "$HTTP_PORT" ] || HTTP_PORT=80

  cd "$DEPLOY_DIR"
  say "构建并启动容器（轻量模式：nginx + backend）"
  docker compose -f "$LITE_COMPOSE" up -d --build
  [ $? -eq 0 ] || die "容器启动失败，查看 'docker-start.sh logs'"

  wait_healthy
  say "容器状态："
  docker compose -f "$LITE_COMPOSE" ps

  ADMIN="$(grep -E '^GUICANG_CONTAINER_ADMIN_USER=' "$ENV_FILE" | cut -d= -f2)"
  PASS="$(grep -E '^GUICANG_CONTAINER_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2)"
  [ -n "$ADMIN" ] || ADMIN=admin
  [ -n "$PASS" ] || PASS=guicang123456

  echo
  echo "============================================================"
  echo "  GuiCang 已启动"
  echo "  访问地址: http://127.0.0.1:${HTTP_PORT}/"
  echo "  登录账号: $ADMIN"
  echo "  登录密码: $PASS   （见 deploy/.env GUICANG_CONTAINER_ADMIN_PASSWORD）"
  echo "  首次访问请完成初始化向导（创建/确认管理员）"
  echo "  存储根:   $(grep -E '^GUICANG_STORAGE_ROOT=' "$ENV_FILE" | cut -d= -f2)"
  echo "  停止:     bash scripts/docker-start.sh stop"
  echo "  日志:     bash scripts/docker-start.sh logs"
  echo "============================================================"
}

cmd_stop() {
  cd "$DEPLOY_DIR"
  docker compose -f "$LITE_COMPOSE" stop
  say "已停止（数据卷保留）"
}

cmd_down() {
  cd "$DEPLOY_DIR"
  docker compose -f "$LITE_COMPOSE" down
  say "已停止并移除容器/网络（数据卷保留）"
}

cmd_status() {
  cd "$DEPLOY_DIR"
  docker compose -f "$LITE_COMPOSE" ps
}

cmd_logs() {
  cd "$DEPLOY_DIR"
  shift || true
  docker compose -f "$LITE_COMPOSE" logs -f --tail=100 "$@"
}

cmd_full() {
  say "完整模式：使用 deploy/docker-compose.yml（宿主机 helper 认证）"
  say "前置：1) bash scripts/setup.sh 已生成 .env  2) 宿主机已部署 guicang-helper（scripts/install-helper.sh）"
  bash "$SCRIPT_DIR/deploy.sh"
}

cmd_help() {
  sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
}

# ---------- 入口 ----------
ACTION="${1:-start}"
case "$ACTION" in
  start|""|stop|restart|status|logs|down|full|help)
    if [ "$ACTION" = "restart" ]; then
      cmd_stop && cmd_start
    elif [ "$ACTION" = "help" ]; then
      cmd_help
    else
      "cmd_$ACTION"
    fi
    ;;
  *)
    die "未知命令: $ACTION（可用: start/stop/restart/status/logs/down/full/help）"
    ;;
esac
