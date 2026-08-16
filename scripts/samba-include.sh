#!/usr/bin/env bash
# =============================================================================
# samba-include.sh — 生成 Samba 共享 include 片段（按 Web 启用用户）
#
# 生成 /etc/samba/guicang-shares.conf：
#   [nas-shared]            path=/nas/shared             valid users=@nasusers, write list=@nasusers
#   [nas-media]             path=/nas/media              valid users=@nasusers, read only
#   [personal-<user>]       path=/nas/personal/<user>    valid users=<user>, write list=<user>
#
# 并在 /etc/samba/smb.conf 末尾追加 include（先备份原文件），testparm 校验后
# smbcontrol reload-config 热加载。
#
# ⚠️ 影响说明（需用户确认后执行）：
#   - 会修改 /etc/samba/smb.conf（追加 include 行，原文件先备份为 .bak-<时间戳>）；
#   - 新增 [nas-shared]/[nas-media]/[personal-*] 共享段，现有共享不受影响；
#   - reload-config 是热加载，不中断已有 Samba 连接。
#
# 用户来源：优先 GUICANG_DB 指向的 SQLite（sys_user 启用用户），
#           否则用 --users "alice,bob" 显式指定。
# 用法: sudo ./scripts/samba-include.sh [--users alice,bob] [--dry-run|--apply]
# =============================================================================
set -uo pipefail

SMBCONF="/etc/samba/smb.conf"
INCLUDE_FILE="/etc/samba/guicang-shares.conf"
STORAGE_ROOT="${GUICANG_STORAGE_ROOT:-/home/wb/nas}"
DB_PATH="${GUICANG_DB:-/data/guicang.db}"
USERS_ARG=""
MODE="dry-run"
[ "${1:-}" = "--apply" ] && MODE="apply"
[ "${1:-}" = "--dry-run" ] && MODE="dry-run"

while [ $# -gt 0 ]; do
  case "$1" in
    --users) USERS_ARG="$2"; shift 2 ;;
    --apply|--dry-run) shift ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

say() { printf '[samba-include] %s\n' "$*"; }
run() {
  if [ "$MODE" = "apply" ]; then "$@"; else say "(dry-run) $*"; fi
}

[ "$(id -u)" -eq 0 ] || { echo "需 root 运行" >&2; exit 1; }
if [ ! -f "$SMBCONF" ]; then
  echo "警告: smb.conf 不存在: $SMBCONF" >&2
  [ "$MODE" = "apply" ] && exit 1
fi
if ! getent group nasusers >/dev/null 2>&1; then
  echo "警告: 组 nasusers 不存在（先跑 install-helper.sh）" >&2
  [ "$MODE" = "apply" ] && exit 1
fi

# ---- 收集启用用户 -----------------------------------------------------------
users=""
if [ -n "$USERS_ARG" ]; then
  users="$USERS_ARG"
elif [ -f "$DB_PATH" ] && command -v sqlite3 >/dev/null 2>&1; then
  users="$(sqlite3 "$DB_PATH" "SELECT username FROM sys_user WHERE enabled = 1;" 2>/dev/null | tr '\n' ',' | sed 's/,$//')"
  say "从 SQLite 读取启用用户: ${users:-（无）}"
else
  echo "未找到用户来源（请用 --users 指定，或确认 GUICANG_DB 可读）" >&2
  exit 1
fi
[ -n "$users" ] || { echo "启用用户列表为空，未生成任何 personal 共享" >&2; }

# ---- 生成 include 文件 ------------------------------------------------------
generate() {
  local out=""
  out+="# GuiCang 生成的共享段（由 samba-include.sh 维护，请勿手改）\n"
  out+="\n"
  out+="[nas-shared]\n"
  out+="    path = $STORAGE_ROOT/shared\n"
  out+="    valid users = @nasusers\n"
  out+="    write list = @nasusers\n"
  out+="    create mask = 0664\n"
  out+="    directory mask = 2775\n"
  out+="    force group = nasusers\n"
  out+="\n"
  out+="[nas-media]\n"
  out+="    path = $STORAGE_ROOT/media\n"
  out+="    valid users = @nasusers\n"
  out+="    read only = yes\n"
  out+="    force group = nasusers\n"
  out+="\n"
  local u
  IFS=',' read -ra user_list <<< "$users"
  for u in "${user_list[@]}"; do
    u="$(echo "$u" | xargs)"
    [ -n "$u" ] || continue
    out+="[personal-$u]\n"
    out+="    path = $STORAGE_ROOT/personal/$u\n"
    out+="    valid users = $u\n"
    out+="    write list = $u\n"
    out+="    create mask = 0664\n"
    out+="    directory mask = 2775\n"
    out+="    force group = nasusers\n"
    out+="\n"
  done
  printf '%b' "$out" > "$INCLUDE_FILE"
  say "已生成 $INCLUDE_FILE"
}

# ---- 主流程 ---------------------------------------------------------------
if [ "$MODE" = "apply" ]; then
  # 备份 smb.conf
  bak="$SMBCONF.bak-$(date +%Y%m%d%H%M%S)"
  cp -a "$SMBCONF" "$bak"
  say "已备份 $SMBCONF -> $bak"

  generate

  # 追加 include（幂等）
  if grep -q "guicang-shares.conf" "$SMBCONF"; then
    say "smb.conf 已含 include，跳过追加"
  else
    printf '\n# GuiCang 共享 include（samba-include.sh 维护）\ninclude = %s\n' "$INCLUDE_FILE" >> "$SMBCONF"
    say "已在 smb.conf 追加 include"
  fi

  # 校验 + 热加载
  if testparm -s "$SMBCONF" >/dev/null 2>&1; then
    say "testparm 校验通过"
    smbcontrol smbd reload-config >/dev/null 2>&1 && say "Samba 配置已热加载"
  else
    echo "testparm 校验失败，请检查 $INCLUDE_FILE；smb.conf 已备份可回滚" >&2
    exit 1
  fi
else
  say "（dry-run）将备份 $SMBCONF、生成 $INCLUDE_FILE（含用户: ${users}）、testparm 校验并热加载"
fi
