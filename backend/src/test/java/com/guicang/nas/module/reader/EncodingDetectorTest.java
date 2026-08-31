package com.guicang.nas.module.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 文本编码探测测试：BOM / UTF-8 严格校验 / GB 兜底。 */
class EncodingDetectorTest {

  @TempDir Path dir;

  private Path write(byte[] bytes, String name) throws Exception {
    Path p = dir.resolve(name);
    Files.write(p, bytes);
    return p;
  }

  @Test
  void utf8NoBom() throws Exception {
    Path p = write("第一章 测试".getBytes(StandardCharsets.UTF_8), "a.txt");
    assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(p));
  }

  @Test
  void utf8Bom() throws Exception {
    byte[] data = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    Path p = write(concat(data, "第一章".getBytes(StandardCharsets.UTF_8)), "b.txt");
    assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(p));
  }

  @Test
  void utf16leBom() throws Exception {
    byte[] data = new byte[] {(byte) 0xFF, (byte) 0xFE};
    Path p = write(concat(data, "第一章".getBytes(StandardCharsets.UTF_16LE)), "c.txt");
    assertEquals(StandardCharsets.UTF_16LE, EncodingDetector.detect(p));
  }

  @Test
  void utf16beBom() throws Exception {
    byte[] data = new byte[] {(byte) 0xFE, (byte) 0xFF};
    Path p = write(concat(data, "第一章".getBytes(StandardCharsets.UTF_16BE)), "d.txt");
    assertEquals(StandardCharsets.UTF_16BE, EncodingDetector.detect(p));
  }

  @Test
  void gbkChinese() throws Exception {
    Path p = write("第一章 测试".getBytes(Charset.forName("GBK")), "e.txt");
    assertEquals(Charset.forName("GB18030"), EncodingDetector.detect(p));
  }

  @Test
  void plainAscii() throws Exception {
    Path p = write("plain ascii text".getBytes(StandardCharsets.US_ASCII), "f.txt");
    assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(p));
  }

  @Test
  void emptyFile() throws Exception {
    Path p = write(new byte[0], "g.txt");
    assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(p));
  }

  @Test
  void mixedChineseAndAsciiUtf8() throws Exception {
    Path p = write("第一章 风云再起 Chapter 1".getBytes(StandardCharsets.UTF_8), "h.txt");
    assertEquals(StandardCharsets.UTF_8, EncodingDetector.detect(p));
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
