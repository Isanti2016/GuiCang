#!/usr/bin/env bash
# =============================================================================
# install-helper.sh — 幂等安装 guicang-helper 到宿主机（需 root）
#
# 作用（全部为新增/覆盖自身文件，不影响现有用户、组与 Samba 配置）：
#   1. 创建组 nasusers(gid 2000)、nasops(gid 2001)（若不存在）
#   2. 创建服务账号 guicang-svc(uid 1002, nologin, 主组 nasops, 附加 nasusers)
#   3. 安装 /usr/local/bin/guicang-helper（root:nasops 750）
#   4. 安装 /usr/local/libexec/guicang/guicang-pam-verify.py（root:root 750）
#   5. 写入 /etc/sudoers.d/guicang 白名单并 visudo -c 校验
#   6. 建立 /var/log/guicang-helper.log
#
# 安全：覆盖已存在文件前先备份为 *.bak-<时间戳>；--dry-run 只预览不执行。
# 用法: sudo ./scripts/install-helper.sh [--dry-run]
# =============================================================================
set -uo pipefail

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

NASUSERS_GID=2000
NASOPS_GID=2001
SVC_UID=1002
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER_SRC="$SCRIPT_DIR/guicang-helper"
PAM_SRC="$SCRIPT_DIR/guicang-pam-verify.py"
HELPER_DST=/usr/local/bin/guicang-helper
PAM_DST=/usr/local/libexec/guicang/guicang-pam-verify.py
SUDOERS_DST=/etc/sudoers.d/guicang
LOG_DST=/var/log/guicang-helper.log

say() { printf '[install] %s\n' "$*"; }
run() {
  if [ "$DRY_RUN" -eq 1 ]; then
    say "(dry-run) $*"
  else
    "$@"
  fi
}

[ "$(id -u)" -eq 0 ] || { echo "必须用 root 运行（或 sudo ./scripts/install-helper.sh）" >&2; exit 1; }
[ -f "$HELPER_SRC" ] || { echo "缺少 $HELPER_SRC" >&2; exit 1; }
[ -f "$PAM_SRC" ] || { echo "缺少 $PAM_SRC" >&2; exit 1; }

# 1. 组
if ! getent group nasusers >/dev/null; then
  say "创建组 nasusers (gid $NASUSERS_GID)"
  run groupadd -g "$NASUSERS_GID" nasusers
else
  say "组 nasusers 已存在，跳过"
fi
if ! getent group nasops >/dev/null; then
  say "创建组 nasops (gid $NASOPS_GID)"
  run groupadd -g "$NASOPS_GID" nasops
else
  say "组 nasops 已存在，跳过"
fi

# 2. 服务账号
if ! getent passwd guicang-svc >/dev/null; then
  say "创建服务账号 guicang-svc (uid $SVC_UID)"
  run useradd -u "$SVC_UID" -m -d /home/guicang-svc -s /usr/sbin/nologin \
    -g nasops -G nasusers guicang-svc
else
  say "服务账号 guicang-svc 已存在，跳过"
fi

# 3/4. 安装脚本（先备份旧文件）
install_file() {
  local src="$1" dst="$2" mode="$3" owner="$4"
  if [ -f "$dst" ]; then
    local bak="$dst.bak-$(date +%Y%m%d%H%M%S)"
    say "备份 $dst -> $bak"
    run cp -a "$dst" "$bak"
  fi
  say "安装 $dst (${mode}, ${owner})"
  run install -o "${owner%%:*}" -g "${owner##*:}" -m "$mode" "$src" "$dst"
}
install_file "$HELPER_SRC" "$HELPER_DST" 750 "root:nasops"
install_file "$PAM_SRC" "$PAM_DST" 750 "root:root"

# 5. sudoers 白名单
sudoers_body='%nasops ALL=(root) NOPASSWD:/usr/local/bin/guicang-helper'
if [ -f "$SUDOERS_DST" ] && grep -q "$HELPER_DST" "$SUDOERS_DST" 2>/dev/null; then
  say "sudoers 白名单已存在，跳过"
else
  say "写入 $SUDOERS_DST"
  if [ "$DRY_RUN" -eq 1 ]; then
    say "(dry-run) 内容: $sudoers_body"
  else
    printf '%s\n' "$sudoers_body" > "$SUDOERS_DST"
    chmod 440 "$SUDOERS_DST"
    visudo -c -f "$SUDOERS_DST" || { echo "visudo 校验失败，已移除非法文件" >&2; rm -f "$SUDOERS_DST"; exit 1; }
  fi
fi

# 6. 日志文件
say "准备日志 $LOG_DST"
run touch "$LOG_DST"
run chmod 640 "$LOG_DST"

if [ "$DRY_RUN" -eq 1 ]; then
  say "dry-run 完成，未做任何更改"
  exit 0
fi

# 7. 自检
say "自检: sudo -n $HELPER_DST health"
if sudo -n "$HELPER_DST" health; then
  say "安装完成，health 通过"
else
  echo "安装完成，但 health 未通过（见上方输出；常见原因：guicang-svc 未创建完成或 PAM 库缺失）" >&2
  exit 1
fi
