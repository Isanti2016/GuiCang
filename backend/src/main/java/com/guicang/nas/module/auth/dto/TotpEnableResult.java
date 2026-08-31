package com.guicang.nas.module.auth.dto;

import java.util.List;

/**
 * 开启两步验证的结果。
 *
 * @param secret         TOTP Base32 密钥（录入 Authenticator）
 * @param recoveryCodes  10 个一次性恢复码（仅此一次返回，请保存）
 */
public record TotpEnableResult(String secret, List<String> recoveryCodes) {}
