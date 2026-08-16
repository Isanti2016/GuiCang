#!/usr/bin/env bash
# =============================================================================
# test-helper.sh — guicang-helper 行为测试（只读/无副作用，不创建/修改系统账号）
#
# 覆盖：语法检查、参数白名单、未知命令、metrics/health JSON 结构、
#       PAM verify 的失败路径（错误密码 / 不存在用户）。
# 用法: ./scripts/test-helper.sh
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELPER="$SCRIPT_DIR/guicang-helper"
PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); printf '  ✓ %s\n' "$1"; }
ko()   { FAIL=$((FAIL + 1)); printf '  ✗ %s\n' "$1"; }
check() { # check <描述> <实际> <期望>
  if [ "$2" = "$3" ]; then ok "$1"; else ko "$1（期望 [$3]，实际 [$2]）"; fi
}

# 环境覆盖：日志与 PAM 校验器指向仓库内路径（不装系统）
export GUICANG_HELPER_LOG="${GUICANG_HELPER_LOG:-/tmp/guicang-helper-test.log}"
export GUICANG_PAM_VERIFY="$SCRIPT_DIR/guicang-pam-verify.py"

echo "== 1. 语法检查 =="
bash -n "$HELPER" && ok "bash -n 语法通过" || ko "bash -n 语法错误"
python3 -m py_compile "$SCRIPT_DIR/guicang-pam-verify.py" && ok "python 语法通过" || ko "python 语法错误"

echo "== 2. 参数白名单与命令分发 =="
out="$(bash "$HELPER" 2>&1)";                check "无参数返回 JSON error"        "$(printf '%s' "$out" | grep -c '"ok":false')" "1"
out="$(bash "$HELPER" nope-cmd 2>&1)";       check "未知命令返回 JSON error"       "$(printf '%s' "$out" | grep -c '"ok":false')" "1"
out="$(bash "$HELPER" verify 'Bad Name!' 2>&1)"; check "非法用户名被白名单拒绝"    "$(printf '%s' "$out" | grep -c '"ok":false')" "1"
out="$(bash "$HELPER" user-add '..' 2>&1)";   check "user-add 非法用户名被拒"      "$(printf '%s' "$out" | grep -c '"ok":false')" "1"
out="$(bash "$HELPER" user-del root 2>&1)";   check "user-del 受保护账号被拒"      "$(printf '%s' "$out" | grep -c '受保护')" "1"

echo "== 3. metrics 输出 JSON 结构 =="
out="$(bash "$HELPER" metrics 2>/dev/null)"
check "metrics ok=true"        "$(printf '%s' "$out" | grep -c '"ok":true')" "1"
for key in cpu load1 load5 load15 mem_total_kb mem_avail_kb swap_total_kb swap_free_kb disk_total_kb disk_avail_kb net_rx_bytes net_tx_bytes uptime_sec; do
  check "metrics 含字段 $key" "$(printf '%s' "$out" | grep -c "\"$key\"")" "1"
done

echo "== 4. health 输出 JSON（可解析） =="
out="$(bash "$HELPER" health 2>/dev/null)"
check "health 输出 ok 或 failed 字段" "$(printf '%s' "$out" | grep -cE '"ok":(true|false)')" "1"

echo "== 5. verify 失败路径（PAM 实际调用，无副作用） =="
out="$(printf 'wrong-password-xyz\n' | bash "$HELPER" verify nobody 2>/dev/null)"
check "不存在用户返回 ok=false" "$(printf '%s' "$out" | grep -cE '"ok": *false')" "1"
out="$(printf 'wrong-password-xyz\n' | bash "$HELPER" verify root 2>/dev/null)"
check "root 错误密码返回 ok=false" "$(printf '%s' "$out" | grep -cE '"ok": *false')" "1"
check "错误输出不泄露密码明文" "$(printf '%s' "$out" | grep -c 'wrong-password-xyz')" "0"

echo "== 6. PAM conversation 回调单测（正路径逻辑，不依赖真实账号） =="
conv_test="$(mktemp /tmp/pam-conv-test-XXXXXX.py)"
cat > "$conv_test" <<'PYEOF'
import ctypes
import importlib.util
import sys

spec = importlib.util.spec_from_file_location("pamv", sys.argv[1])
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

conv_obj = m.make_conv("secret-pass")
msgs = (ctypes.POINTER(m.PamMessage) * 2)()
for i, style in enumerate([m.PAM_PROMPT_ECHO_OFF, m.PAM_TEXT_INFO]):
    msgs[i] = ctypes.pointer(m.PamMessage(style, b"prompt"))
# 模拟 PAM 传入的 struct pam_response **：一个存放数组首指针的槽
buf = (ctypes.POINTER(m.PamResponse) * 1)()
ret = conv_obj.conv(2, msgs, buf, None)
assert ret == m.PAM_SUCCESS, "conv 返回值非 PAM_SUCCESS"
# PAM 约定：*resp 指向 struct pam_response 数组（非指针数组），按结构体直接读取
region_addr = ctypes.cast(buf[0], ctypes.c_void_p).value
resp_arr = (m.PamResponse * 2).from_address(region_addr)
r0 = resp_arr[0]
r1 = resp_arr[1]
assert r0.resp == b"secret-pass", f"ECHO_OFF 未回传密码: {r0.resp!r}"
assert r1.resp in (None, b""), f"非密码提示不应回传内容: {r1.resp!r}"
print("conv OK")
PYEOF
out="$(python3 "$conv_test" "$SCRIPT_DIR/guicang-pam-verify.py" 2>&1)"
rm -f "$conv_test"
check "回调正确回传密码(ECHO_OFF)/忽略提示(TEXT_INFO)" "$(printf '%s' "$out" | grep -c 'conv OK')" "1"

echo
echo "结果: $PASS 通过, $FAIL 失败"
[ "$FAIL" -eq 0 ] || exit 1
