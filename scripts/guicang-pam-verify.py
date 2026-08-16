#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""guicang PAM 密码校验器（ctypes 直调 libpam，无第三方依赖）。

用法: echo '密码' | guicang-pam-verify.py <username>
密码仅经 stdin 传入，绝不进入 argv / 环境变量 / 日志。
输出统一 JSON: {"ok": true, "uid": .., "gid": .., "home": .., "shell": ..}
失败: {"ok": false, "error": ".."}
"""
import ctypes
import ctypes.util
import json
import pwd
import sys

PAM_PROMPT_ECHO_OFF = 1
PAM_PROMPT_ECHO_ON = 2
PAM_ERROR_MSG = 3
PAM_TEXT_INFO = 4
PAM_SUCCESS = 0
PAM_BUF_ERR = 10


class PamHandle(ctypes.Structure):
    pass


class PamMessage(ctypes.Structure):
    _fields_ = [("msg_style", ctypes.c_int), ("msg", ctypes.c_char_p)]


class PamResponse(ctypes.Structure):
    _fields_ = [("resp", ctypes.c_char_p), ("resp_retcode", ctypes.c_int)]


PamConvFunc = ctypes.CFUNCTYPE(
    ctypes.c_int,
    ctypes.c_int,
    ctypes.POINTER(ctypes.POINTER(PamMessage)),
    ctypes.POINTER(ctypes.POINTER(PamResponse)),
    ctypes.c_void_p,
)


class PamConv(ctypes.Structure):
    _fields_ = [("conv", PamConvFunc), ("appdata_ptr", ctypes.c_void_p)]


def make_conv(password: str) -> PamConv:
    """构造 PAM conversation：只在要求输入密码时回传密码，其余提示忽略。

    重要：按 PAM 约定，响应数组与密码串必须用 libc malloc 分配 —— PAM 在会话结束后
    会调用 free() 释放它们；若用 Python 托管内存会导致 free(): invalid pointer 崩溃。
    """

    password_bytes = password.encode("utf-8")
    libc = ctypes.CDLL(None)
    libc.malloc.restype = ctypes.c_void_p
    libc.malloc.argtypes = [ctypes.c_size_t]

    def conv(nmsgs, msgs, pmsg, data):
        region = libc.malloc(ctypes.sizeof(PamResponse) * nmsgs)
        if not region:
            return PAM_BUF_ERR
        responses = (PamResponse * nmsgs).from_address(region)
        for i in range(nmsgs):
            style = msgs[i].contents.msg_style
            if style == PAM_PROMPT_ECHO_OFF:
                buf = libc.malloc(len(password_bytes) + 1)
                if not buf:
                    return PAM_BUF_ERR
                # 必须写入 NUL 终止符，否则 PAM 会越界读未初始化内存
                ctypes.memmove(buf, password_bytes + b"\x00", len(password_bytes) + 1)
                responses[i].resp = ctypes.cast(buf, ctypes.c_char_p)
            else:
                responses[i].resp = None
            responses[i].resp_retcode = 0
        # pmsg[0] 是 struct pam_response *：直接把 region 地址写入槽内存，
        # 不经过临时 ctypes 对象（避免对象生命周期导致的偶发空指针）
        ctypes.cast(pmsg, ctypes.POINTER(ctypes.c_void_p))[0] = region
        return PAM_SUCCESS

    return PamConv(conv=PamConvFunc(conv), appdata_ptr=None)


def main() -> int:
    if len(sys.argv) != 2:
        print(json.dumps({"ok": False, "error": "用法: verify <username>"}))
        return 2
    username = sys.argv[1]

    libpam_path = ctypes.util.find_library("pam")
    if not libpam_path:
        print(json.dumps({"ok": False, "error": "libpam 不可用"}))
        return 1
    libpam = ctypes.CDLL(libpam_path)
    libpam.pam_start.restype = ctypes.c_int
    libpam.pam_authenticate.restype = ctypes.c_int
    libpam.pam_acct_mgmt.restype = ctypes.c_int
    libpam.pam_end.restype = ctypes.c_int

    password = sys.stdin.read().rstrip("\n")

    handle = ctypes.POINTER(PamHandle)()
    conv = make_conv(password)
    conv_ptr = ctypes.pointer(conv)
    ret = libpam.pam_start(
        b"login", username.encode("utf-8"), conv_ptr, ctypes.byref(handle)
    )
    if ret != PAM_SUCCESS:
        print(json.dumps({"ok": False, "error": "pam_start 失败"}))
        return 1

    try:
        ret = libpam.pam_authenticate(handle, 0)
        if ret != PAM_SUCCESS:
            print(json.dumps({"ok": False, "error": "用户名或密码错误"}))
            return 1
        ret = libpam.pam_acct_mgmt(handle, 0)
        if ret != PAM_SUCCESS:
            print(json.dumps({"ok": False, "error": "账号被禁用或不可登录"}))
            return 1
    finally:
        libpam.pam_end(handle, ret)

    try:
        pw = pwd.getpwnam(username)
    except KeyError:
        print(json.dumps({"ok": False, "error": "用户不存在"}))
        return 1
    print(
        json.dumps(
            {
                "ok": True,
                "uid": pw.pw_uid,
                "gid": pw.pw_gid,
                "home": pw.pw_dir,
                "shell": pw.pw_shell,
            }
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
