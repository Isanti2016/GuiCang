package com.guicang.nas.module.reader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文本文件编码探测：BOM 优先 → 严格 UTF-8 试解码 → GB18030 兜底。
 *
 * <p>中文网络小说 txt 绝大多数为 UTF-8 或 GBK/GB2312（GB18030 为超集）；纯 ASCII 文件同时是合法
 * UTF-8，故 UTF-8 严格校验通过即判定 UTF-8，无需区分 ASCII。
 */
public final class EncodingDetector {

  /** 探测读取的头部字节数。 */
  static final int PROBE_BYTES = 65536;

  private EncodingDetector() {}

  /**
   * 探测文件编码。
   *
   * @param file 文件路径
   * @return 探测到的字符集
   * @throws IOException 读取失败
   */
  public static Charset detect(Path file) throws IOException {
    byte[] head = readHead(file);
    if (head.length == 0) {
      return StandardCharsets.UTF_8;
    }
    if (startsWith(head, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
      return StandardCharsets.UTF_8;
    }
    if (startsWith(head, (byte) 0xFF, (byte) 0xFE)) {
      return StandardCharsets.UTF_16LE;
    }
    if (startsWith(head, (byte) 0xFE, (byte) 0xFF)) {
      return StandardCharsets.UTF_16BE;
    }
    if (isValidUtf8(head)) {
      return StandardCharsets.UTF_8;
    }
    // GB18030 是 GBK/GB2312 的超集，可覆盖绝大多数中文编码
    return Charset.forName("GB18030");
  }

  /** 读取文件头部字节（不超过 PROBE_BYTES）。 */
  static byte[] readHead(Path file) throws IOException {
    byte[] buf = new byte[PROBE_BYTES];
    try (InputStream in = Files.newInputStream(file)) {
      int off = 0;
      while (off < buf.length) {
        int n = in.read(buf, off, buf.length - off);
        if (n < 0) {
          break;
        }
        off += n;
      }
      if (off == buf.length) {
        return buf;
      }
      byte[] trimmed = new byte[off];
      System.arraycopy(buf, 0, trimmed, 0, off);
      return trimmed;
    }
  }

  /**
   * 严格 UTF-8 校验：任何非法字节序列即判失败。
   *
   * <p>注意：探测头可能恰好截断在一个多字节 UTF-8 字符中间（如 64KB 边界正好落在汉字字节
   * 之间），此时完整序列校验必然失败。为避免把 UTF-8 文件误判为 GB18030，逐字节去掉尾部
   * 1~3 字节重试（UTF-8 单字符最长 4 字节）；GB18030 双字节序列（0x81-0xFE 开头）不会
   * 因此误通过 UTF-8 校验。
   */
  static boolean isValidUtf8(byte[] bytes) {
    int maxDrop = Math.min(3, bytes.length);
    for (int drop = 0; drop <= maxDrop; drop++) {
      int len = bytes.length - drop;
      CharsetDecoder decoder =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT);
      try {
        decoder.decode(ByteBuffer.wrap(bytes, 0, len));
        return true;
      } catch (CharacterCodingException ignored) {
        // 尝试去掉更多尾部字节（截断场景）
      }
    }
    return false;
  }

  private static boolean startsWith(byte[] data, byte... prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
