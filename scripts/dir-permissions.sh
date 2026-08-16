#!/usr/bin/env bash
# =============================================================================
# dir-permissions.sh — 建立 NAS 目录结构权限（nasusers 组 + setgid 继承）
#
# 目标结构（手册 2.6 / 详细设计 3.6）：
#   shared/              2775 root:nasusers  共享，组内可写（setgid 继承组）
#   media/{photos,videos,music}/  2755 root:nasusers  媒体，读多写少
#   backups/             0750 root:nasusers  备份，默认仅管理员
#   personal/<user>/     0750 user:nasusers  个人，仅本人 + 管理员
#   private/             0700 app_data       保留现状，不纳入 Web 管理
#
# 安全（不影响现有数据与功能）：
#   1. 只建目录 + 调整"顶层目录"权限，绝不递归改动现有文件；
#   2. 执行前先对现有目录做权限快照（/var/backups/guicang-dir-permissions-*.txt），
#      可用快照回滚（chmod/chown 逐条恢复）；
#   3. 默认 --dry-run 预览，确认后 --apply；
#   4. 不改动现有 Samba 配置（那是 samba-include.sh 的事）。
#
# 用法: sudo ./scripts/dir-permissions.sh [--dry-run|--apply]
# =============================================================================
set -uo pipefail

STORAGE_ROOT="${GUICANG_STORAGE_ROOT:-/home/wb/nas}"
NASUSERS_GROUP="nasusers"
MODE="dry-run"
[ "${1:-}" = "--apply" ] && MODE="apply"
[ "${1:-}" = "--dry-run" ] && MODE="dry-run"

SNAPSHOT_DIR="${GUICANG_SNAPSHOT_DIR:-/var/backups}"

say() { printf '[dir-permissions] %s\n' "$*"; }
run() {
  if [ "$MODE" = "apply" ]; then
    "$@"
  else
    say "(dry-run) $*"
  fi
}

# ---- 预检查（dry-run 只提示不阻断，便于先预览） --------------------------------
[ "$(id -u)" -eq 0 ] || { echo "需 root 运行（sudo ./scripts/dir-permissions.sh）" >&2; exit 1; }
if ! getent group "$NASUSERS_GROUP" >/dev/null 2>&1; then
  echo "警告: 组 $NASUSERS_GROUP 不存在（请先运行 install-helper.sh 后再 --apply）" >&2
  [ "$MODE" = "apply" ] && exit 1
fi
if [ ! -d "$STORAGE_ROOT" ]; then
  echo "警告: 存储根不存在: $STORAGE_ROOT" >&2
  [ "$MODE" = "apply" ] && exit 1
fi

# ---- 权限快照 -------------------------------------------------------------
snapshot_file="$SNAPSHOT_DIR/guicang-dir-permissions-$(date +%Y%m%d%H%M%S).txt"
snapshot() {
  if [ "$MODE" = "apply" ]; then
    mkdir -p "$SNAPSHOT_DIR"
    (cd "$STORAGE_ROOT" && find . -maxdepth 3 -type d -printf '%m %u %g %p\n' | sort) \
      > "$snapshot_file"
    say "权限快照已保存: $snapshot_file"
  else
    say "(dry-run) 将保存权限快照至 $snapshot_file"
  fi
}

# ---- 目录处理：只建目录 + 设顶层权限，不递归 ----------------------------------
setup_dir() { # setup_dir <相对路径> <mode> <owner:group>
  local rel="$1" mode="$2" owner="$3"
  local target="$STORAGE_ROOT/$rel"
  if [ -d "$target" ]; then
    say "目录 $rel 已存在，仅调整顶层权限（不递归，不影响已有文件）"
    run chmod "$mode" "$target"
    run chown "${owner%%:*}:${owner##*:}" "$target"
  else
    say "创建目录 $rel（$mode ${owner}）"
    run install -d -o "${owner%%:*}" -g "${owner##*:}" -m "$mode" "$target"
  fi
}

# ---- 主流程 ---------------------------------------------------------------
snapshot

setup_dir "shared"           2775 "root:$NASUSERS_GROUP"
setup_dir "media"            2755 "root:$NASUSERS_GROUP"
setup_dir "media/photos"     2755 "root:$NASUSERS_GROUP"
setup_dir "media/videos"     2755 "root:$NASUSERS_GROUP"
setup_dir "media/music"      2755 "root:$NASUSERS_GROUP"
setup_dir "backups"          0750 "root:$NASUSERS_GROUP"
setup_dir "personal"         0750 "root:$NASUSERS_GROUP"

if [ "$MODE" = "dry-run" ]; then
  say "dry-run 完成，未做任何更改；确认后执行: sudo ./scripts/dir-permissions.sh --apply"
else
  say "应用完成（快照: $snapshot_file，回滚参考 find 快照逐条 chmod/chown）"
fi
