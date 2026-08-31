package com.guicang.nas.module.reader;

import com.guicang.nas.common.BizException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * TXT 小说解析器：编码探测 + 章节正则识别 + 字节偏移索引。
 *
 * <p>大文件采用一次流式扫描建立「章节 → 字节偏移」索引，读正文按偏移随机访问，避免整文件载入内存。
 * 文件头（第一个章节前的非空内容，如书名/简介）并入第一章正文，不丢内容。
 */
@Component
public class TxtNovelParser {

  /** 解析文件大小上限：500MB，防止病态文件拖垮服务。 */
  static final long MAX_FILE_BYTES = 500L * 1024 * 1024;

  /** 单章正文读取上限：4MB，防止单章超大导致内存/响应问题。 */
  static final int MAX_CHAPTER_BYTES = 4 * 1024 * 1024;

  /** 章节数上限：防止逐行广告被误识别为章节。 */
  static final int MAX_CHAPTERS = 5000;

  /** 中文数字/阿拉伯数字。 */
  private static final String NUM = "[0-9零一二三四五六七八九十百千万两]+";

  /** 章节行正则：第X章/回/节/卷/集/部/篇 + 楔子/序章/序言/引子/引言/前言/尾声/后记/番外/外传/终章。 */
  static final Pattern CHAPTER_PATTERN =
      Pattern.compile(
          "^(?:(?:第\\s*"
              + NUM
              + "\\s*[章节回卷集部篇])|(?:楔子|序章|序言|引子|引言|前言|尾声|后记|番外(?:篇)?|外传|终章))(?:[\\s·:：、\\-—]|$).*$");

  /** 解析结果：章节索引（含字节偏移）+ 探测到的编码。 */
  public record ParsedTxt(List<TxtChapter> chapters, Charset charset) {}

  /** 章节索引：标题 + 字节区间 [startOffset, endOffset)。 */
  public record TxtChapter(int index, String title, long startOffset, long endOffset) {}

  private static final Pattern EXT = Pattern.compile("\\.txt$", Pattern.CASE_INSENSITIVE);

  /**
   * 解析 TXT 文件。
   *
   * @param file 文件路径
   * @return 章节索引与编码
   * @throws IOException 读取失败
   */
  public ParsedTxt parse(Path file) throws IOException {
    long size = Files.size(file);
    if (size > MAX_FILE_BYTES) {
      throw new BizException("文本文件过大（>500MB），暂不支持解析为小说");
    }
    Charset charset = EncodingDetector.detect(file);
    List<TxtChapter> raw = scanChapters(file, charset);
    List<TxtChapter> chapters = buildRanges(raw, size, file);
    if (chapters.isEmpty()) {
      String title = titleFromFileName(file);
      chapters.add(new TxtChapter(0, title, 0, size));
    }
    return new ParsedTxt(chapters, charset);
  }

  /** 读取指定章节正文（按字节偏移随机访问）。 */
  public String readChapter(Path file, ParsedTxt parsed, int index) throws IOException {
    List<TxtChapter> chapters = parsed.chapters();
    if (index < 0 || index >= chapters.size()) {
      throw new BizException("章节索引越界");
    }
    TxtChapter c = chapters.get(index);
    long end = Math.min(c.endOffset(), c.startOffset() + MAX_CHAPTER_BYTES);
    byte[] bytes = readRange(file, c.startOffset(), end);
    String text = new String(bytes, parsed.charset());
    if (!text.isEmpty() && (text.charAt(0) == '\uFEFF' || text.charAt(0) == '\uFFFE')) {
      text = text.substring(1);
    }
    return trimBlankLines(text);
  }

  /** 当前解析支持的扩展名。 */
  public boolean supports(String fileName) {
    return fileName != null && EXT.matcher(fileName).find();
  }

  /** 书名缺省取文件名（去 .txt 后缀）。 */
  static String titleFromFileName(Path file) {
    String name = file.getFileName().toString();
    return EXT.matcher(name).replaceFirst("");
  }

  /** 流式扫描：逐行识别章节标题，记录每章起始字节偏移。 */
  private List<TxtChapter> scanChapters(Path file, Charset charset) throws IOException {
    List<TxtChapter> chapters = new ArrayList<>();
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r");
        InputStream in = new BufferedInputStream(new FileInputStream(raf.getFD()), 1 << 16)) {
      long position = 0;
      long lineStart = 0;
      ByteArrayOutputStream line = new ByteArrayOutputStream(128);
      int b;
      while ((b = in.read()) != -1) {
        position++;
        if (b == '\n') {
          handleLine(line.toByteArray(), lineStart, charset, chapters);
          lineStart = position;
          line.reset();
        } else if (b != '\r') {
          line.write(b);
        }
      }
      if (line.size() > 0) {
        handleLine(line.toByteArray(), lineStart, charset, chapters);
      }
    }
    return chapters;
  }

  /** 处理一行：去 BOM/空白后若命中章节正则则登记（带偏移），并受章节数上限保护。 */
  private void handleLine(
      byte[] bytes, long lineStart, Charset charset, List<TxtChapter> chapters) {
    String raw = new String(bytes, charset);
    if (!raw.isEmpty() && (raw.charAt(0) == '\uFEFF' || raw.charAt(0) == '\uFFFE')) {
      raw = raw.substring(1);
    }
    String line = raw.trim();
    if (line.isEmpty() || chapters.size() >= MAX_CHAPTERS) {
      return;
    }
    if (isChapterTitle(line)) {
      chapters.add(new TxtChapter(chapters.size(), line, lineStart, -1));
    }
  }

  /** 是否命中章节标题行。 */
  static boolean isChapterTitle(String line) {
    return CHAPTER_PATTERN.matcher(line).matches();
  }

  /** 填充章节字节区间：第 i 章 [start, nextStart)，末章到文件尾；首章从 0 起（并入文件头）。 */
  private List<TxtChapter> buildRanges(List<TxtChapter> raw, long fileSize, Path file) {
    if (raw.isEmpty()) {
      return new ArrayList<>();
    }
    List<TxtChapter> out = new ArrayList<>(raw.size());
    for (int i = 0; i < raw.size(); i++) {
      TxtChapter c = raw.get(i);
      long start = i == 0 ? 0 : c.startOffset();
      long end = i + 1 < raw.size() ? raw.get(i + 1).startOffset() : fileSize;
      out.add(new TxtChapter(i, c.title(), start, end));
    }
    return out;
  }

  /** 读取文件字节区间 [start, end)。 */
  private byte[] readRange(Path file, long start, long end) throws IOException {
    int len = (int) (end - start);
    byte[] buf = new byte[len];
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
      raf.seek(start);
      raf.readFully(buf);
    }
    return buf;
  }

  /** 去掉首尾空白行，压缩正文内多余空行（最多连续 1 个空行）。 */
  static String trimBlankLines(String text) {
    String[] lines = text.split("\\r?\\n", -1);
    StringBuilder sb = new StringBuilder(text.length());
    boolean blankPending = false;
    for (String line : lines) {
      boolean blank = line.isBlank();
      if (blank) {
        blankPending = sb.length() > 0;
        continue;
      }
      if (blankPending && sb.length() > 0) {
        sb.append('\n');
      }
      blankPending = false;
      sb.append(line.strip()).append('\n');
    }
    int len = sb.length();
    while (len > 0 && sb.charAt(len - 1) == '\n') {
      len--;
    }
    sb.setLength(len);
    return sb.toString();
  }
}
