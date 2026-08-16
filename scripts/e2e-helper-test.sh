#!/usr/bin/env bash
# =============================================================================
# e2e-helper-test.sh — guicang-helper 端到端测试（会临时创建并删除系统账号！）
#
# 覆盖 helper 真实能力：user-add（建账号+设密码+Samba）→ verify 正/反路径 →
# passwd（改密）→ verify 新密码 → user-del（含清理）。
# 安全：trap 保证任何失败都会清理临时账号；不触碰现有用户与数据。
# 用法: sudo ./scripts/e2e-helper-test.sh
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER="$SCRIPT_DIR/guicang-helper"
TEST_USER="guicang-e2e-test"
TEST_PASS="Guicang-E2e-2026!"
NASUSERS_CREATED=0
PASS=0
FAIL=0

ok() { PASS=$((PASS + 1)); printf '  ✓ %s\n' "$1"; }
ko() { FAIL=$((FAIL + 1)); printf '  ✗ %s\n' "$1"; }

export GUICANG_HELPER_LOG="${GUICANG_HELPER_LOG:-/tmp/guicang-helper-e2e.log}"
export GUICANG_PAM_VERIFY="$SCRIPT_DIR/guicang-pam-verify.py"

cleanup() {
  if getent passwd "$TEST_USER" >/dev/null 2>&1; then
    echo "[清理] 删除临时账号 $TEST_USER"
    bash "$HELPER" user-del "$TEST_USER" --remove-home >/dev/null 2>&1
    pdbedit -x "$TEST_USER" >/dev/null 2>&1 || true
  fi
  if [ "$NASUSERS_CREATED" = "1" ] && getent group nasusers >/dev/null 2>&1; then
    echo "[清理] 删除临时组 nasusers"
    groupdel nasusers >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

[ "$(id -u)" -eq 0 ] || { echo "端到端测试需要 root（会临时建/删账号）" >&2; exit 1; }
getent passwd "$TEST_USER" >/dev/null 2>&1 && { echo "临时账号已存在，请先清理" >&2; exit 1; }
if ! getent group nasusers >/dev/null 2>&1; then
  echo "[准备] 临时创建组 nasusers (gid 2000)"
  groupadd -g 2000 nasusers || { echo "创建临时组失败" >&2; exit 1; }
  NASUSERS_CREATED=1
fi

echo "== user-add（建账号 + 设密码）=="
out="$(printf '%s\n' "$TEST_PASS" | bash "$HELPER" user-add "$TEST_USER" --display "e2e测试" 2>/dev/null)"
[ "$(printf '%s' "$out" | grep -cE '"ok": *true')" = "1" ] && ok "user-add 成功" || ko "user-add 失败: $out"

echo "== verify 正路径（正确密码）=="
out="$(printf '%s\n' "$TEST_PASS" | bash "$HELPER" verify "$TEST_USER" 2>/dev/null)"
[ "$(printf '%s' "$out" | grep -cE '"ok": *true')" = "1" ] && ok "正确密码认证通过" || ko "正确密码认证失败: $out"
uid="$(printf '%s' "$out" | grep -oE '"uid": *[0-9]*' | grep -oE '[0-9]+' | head -1)"
[ -n "$uid" ] && [ "$uid" -gt 1000 ] && ok "返回有效 uid=$uid" || ko "uid 异常: $out"

echo "== verify 反路径（错误密码）=="
out="$(printf '%s\n' "WrongPass-2026!" | bash "$HELPER" verify "$TEST_USER" 2>/dev/null)"
[ "$(printf '%s' "$out" | grep -cE '"ok": *false')" = "1" ] && ok "错误密码被拒" || ko "错误密码未被拒: $out"

echo "== passwd（改密）=="
out="$(printf '%s\n' "New-Guicang-2026!" | bash "$HELPER" passwd "$TEST_USER" 2>/dev/null)"
[ "$(printf '%s' "$out" | grep -cE '"ok": *true')" = "1" ] && ok "改密成功" || ko "改密失败: $out"
out="$(printf '%s\n' "New-Guicang-2026!" | bash "$HELPER" verify "$TEST_USER" 2>/dev/null)"
[ "$(printf '%s' "$out" | grep -cE '"ok": *true')" = "1" ] && ok "新密码认证通过" || ko "新密码认证失败: $out"

echo "== user-del（删除并清理）=="
out="$(bash "$HELPER" user-del "$TEST_USER" --remove-home 2>/dev/null)"
[ "$(printf '%s' "$out" | grep -cE '"ok": *true')" = "1" ] && ok "删除成功" || ko "删除失败: $out"
getent passwd "$TEST_USER" >/dev/null 2>&1 && ko "账号仍存在（清理失败）" || ok "账号已移除"
[ -d "/home/$TEST_USER" ] && ko "家目录仍存在" || ok "家目录已移除"
# 不解除 trap：让 EXIT 清理统一处理（幂等，组清理在 EXIT 时执行）

echo
echo "结果: $PASS 通过, $FAIL 失败"
[ "$FAIL" -eq 0 ] || exit 1
