#!/usr/bin/env bash
# =============================================================================
# backup.sh — 数据备份（SQLite + 本地配置 + 目录权限快照），保留最近 7 份
#
# 注意：/home/wb/nas 是用户数据真源，本脚本不备份 NAS 文件（由用户自行决定策略）。
# 用法: sudo bash scripts/backup.sh [--dir /backup/guicang]
# =============================================================================
set -uo pipefail

BACKUP_DIR="${GUICANG_BACKUP_DIR:-/var/backups/guicang}"
KEEP=7
[ "${1:-}" = "--dir" ] && BACKUP_DIR="$2"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

say() { printf '[backup] %s\n' "$*"; }

[ "$(id -u)" -eq 0 ] || { echo "建议用 root 运行（备份 /etc/guicang 与数据库文件）" >&2; }

STAMP="$(date +%Y%m%d%H%M%S)"
DEST="$BACKUP_DIR/$STAMP"
mkdir -p "$DEST"

# 1. SQLite（容器内 /data/guicang.db；本机开发 backend/data/guicang.db）
DB_SOURCE=""
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^guicang-backend$'; then
  DB_SOURCE="docker exec guicang-backend cat /data/guicang.db"
elif [ -f "$REPO_ROOT/backend/data/guicang.db" ]; then
  DB_SOURCE="cat $REPO_ROOT/backend/data/guicang.db"
fi
if [ -n "$DB_SOURCE" ]; then
  sqlite3 /tmp/guicang-backup.db ".backup '$DEST/guicang.db'" 2>/dev/null \
    || bash -c "$DB_SOURCE" > "$DEST/guicang.db" 2>/dev/null
  say "已备份数据库: $DEST/guicang.db"
else
  say "未找到 SQLite 数据库（跳过）"
fi

# 2. 本地配置
if [ -d /etc/guicang ]; then
  cp -a /etc/guicang "$DEST/etc-guicang"
  say "已备份 /etc/guicang"
fi
if [ -f "$REPO_ROOT/deploy/.env" ]; then
  cp -a "$REPO_ROOT/deploy/.env" "$DEST/env"
  say "已备份 deploy/.env"
fi

# 3. 目录权限快照（NAS 顶层目录）
if [ -d /home/wb/nas ]; then
  (cd /home/wb/nas && find . -maxdepth 3 -type d -printf '%m %u %g %p\n' | sort) \
    > "$DEST/nas-dir-permissions.txt"
  say "已备份 NAS 目录权限快照"
fi

# 4. 清理旧备份（保留最近 $KEEP 份）
ls -1dt "$BACKUP_DIR"/*/ 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
  say "清理旧备份: $old"
  rm -rf "$old"
done

say "备份完成: $DEST"
