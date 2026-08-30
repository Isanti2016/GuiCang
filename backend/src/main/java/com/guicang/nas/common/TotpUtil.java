package com.guicang.nas.common;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** TOTP（RFC 6238）工具：生成密钥、计算/校验 6 位动态码（±1 时间窗容错）。 */
public final class TotpUtil {

  private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private TotpUtil() {}

  /** 生成 20 字节随机密钥（Base32 编码，可直接输入 Authenticator）。 */
  public static String generateSecret() {
    byte[] bytes = new byte[20];
    new SecureRandom().nextBytes(bytes);
    return base32Encode(bytes);
  }

  /** 计算某时间戳的 6 位动态码。 */
  public static String code(String secret, long timestampMillis) {
    byte[] key = base32Decode(secret);
    long counter = timestampMillis / 30000L;
    byte[] msg = new byte[8];
    for (int i = 0; i < 8; i++) {
      msg[7 - i] = (byte) (counter >>> (8 * i));
    }
    byte[] hash = hmacSha1(key, msg);
    int offset = hash[hash.length - 1] & 0xf;
    int binary = ((hash[offset] & 0x7f) << 24)
        | ((hash[offset + 1] & 0xff) << 16)
        | ((hash[offset + 2] & 0xff) << 8)
        | (hash[offset + 3] & 0xff);
    return String.format("%06d", binary % 1000000);
  }

  /** 校验动态码（当前时间窗 ±1 容错）。 */
  public static boolean verify(String secret, String code) {
    if (secret == null || code == null || code.isBlank()) {
      return false;
    }
    long now = System.currentTimeMillis();
    for (int i = -1; i <= 1; i++) {
      if (code(secret, now + i * 30000L).equals(code.trim())) {
        return true;
      }
    }
    return false;
  }

  private static byte[] hmacSha1(byte[] key, byte[] msg) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      return mac.doFinal(msg);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA1 不可用", e);
    }
  }

  private static String base32Encode(byte[] data) {
    StringBuilder sb = new StringBuilder();
    int buffer = 0;
    int bits = 0;
    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xff);
      bits += 8;
      while (bits >= 5) {
        sb.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1f));
        bits -= 5;
      }
    }
    if (bits > 0) {
      sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1f));
    }
    return sb.toString();
  }

  private static byte[] base32Decode(String input) {
    String s = input.toUpperCase().replace("=", "").replace(" ", "");
    int buffer = 0;
    int bits = 0;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (char c : s.toCharArray()) {
      int val = BASE32.indexOf(c);
      if (val < 0) {
        continue;
      }
      buffer = (buffer << 5) | val;
      bits += 5;
      if (bits >= 8) {
        out.write((buffer >> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return out.toByteArray();
  }
}
