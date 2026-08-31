package com.guicang.nas.module.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guicang.nas.common.BizException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TXT 小说解析测试：章节正则、编码、字节偏移、边界保护。 */
class TxtNovelParserTest {

  @TempDir Path dir;

  private final TxtNovelParser parser = new TxtNovelParser();

  private Path file;

  @BeforeEach
  void setUp() throws Exception {
    file = dir.resolve("仙路风云.txt");
  }

  @Test
  void arabicChapterNumbers() throws Exception {
    Files.writeString(
        file,
        "第一章 重生归来\n正文一。\n第2章 初次修炼\n正文二。\n第003章 下山\n正文三。\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(3, parsed.chapters().size());
    assertEquals("第一章 重生归来", parsed.chapters().get(0).title());
    assertEquals("第2章 初次修炼", parsed.chapters().get(1).title());
    assertEquals("第003章 下山", parsed.chapters().get(2).title());
  }

  @Test
  void chineseNumberChapters() throws Exception {
    Files.writeString(
        file,
        "第一章 起点\n正文。\n第一百二十三章 巅峰\n正文。\n第一千零二章 终局\n正文。\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(3, parsed.chapters().size());
    assertEquals("第一百二十三章 巅峰", parsed.chapters().get(1).title());
    assertEquals("第一千零二章 终局", parsed.chapters().get(2).title());
  }

  @Test
  void volumesAndSpecials() throws Exception {
    Files.writeString(
        file,
        "第一卷 少年时代\n第一章 觉醒\n正文。\n楔子 天外来客\n正文。\n番外篇 婚礼\n正文。\n尾声\n正文。\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    List<TxtNovelParser.TxtChapter> chapters = parsed.chapters();
    assertEquals(5, chapters.size());
    assertEquals("第一卷 少年时代", chapters.get(0).title());
    assertEquals("楔子 天外来客", chapters.get(2).title());
    assertEquals("番外篇 婚礼", chapters.get(3).title());
    assertEquals("尾声", chapters.get(4).title());
  }

  @Test
  void nonChapterLinesIgnored() throws Exception {
    Files.writeString(
        file,
        "作品简介：这是一个修仙的故事。\n他说道：「第一章不是这样的。」\n第一章 开篇\n正文。\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(1, parsed.chapters().size());
    assertEquals("第一章 开篇", parsed.chapters().get(0).title());
  }

  @Test
  void chapterTitleMatcher() {
    assertTrue(TxtNovelParser.isChapterTitle("第一章 重生"));
    assertTrue(TxtNovelParser.isChapterTitle("第1章 开始"));
    assertTrue(TxtNovelParser.isChapterTitle("第 12 章 中间有空格"));
    assertTrue(TxtNovelParser.isChapterTitle("第九百九十九章"));
    assertTrue(TxtNovelParser.isChapterTitle("楔子"));
    assertTrue(TxtNovelParser.isChapterTitle("序章·过往"));
    assertTrue(TxtNovelParser.isChapterTitle("番外：冬日婚礼"));
    assertTrue(TxtNovelParser.isChapterTitle("后记"));
    assertFalse(TxtNovelParser.isChapterTitle("他说道：第一章是错误示范"));
    assertFalse(TxtNovelParser.isChapterTitle("第一章的内容不该在这里"));
    assertFalse(TxtNovelParser.isChapterTitle("这是一个普通的正文段落"));
  }

  @Test
  void gbkEncoding() throws Exception {
    Files.write(
        file,
        "第一章 江湖\n正文一。\n第二章 恩怨\n正文二。\n".getBytes(Charset.forName("GBK")));
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(Charset.forName("GB18030"), parsed.charset());
    assertEquals(2, parsed.chapters().size());
    String chapter = parser.readChapter(file, parsed, 1);
    assertTrue(chapter.contains("第二章 恩怨"));
    assertTrue(chapter.contains("正文二"));
  }

  @Test
  void utf8BomFirstLine() throws Exception {
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    Files.write(file, concat(bom, "第一章 有BOM\n正文。\n".getBytes(StandardCharsets.UTF_8)));
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(1, parsed.chapters().size());
    assertEquals("第一章 有BOM", parsed.chapters().get(0).title());
  }

  @Test
  void emptyFileSingleChapter() throws Exception {
    Files.writeString(file, "", StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(1, parsed.chapters().size());
    assertEquals("仙路风云", parsed.chapters().get(0).title());
    assertEquals("", parser.readChapter(file, parsed, 0));
  }

  @Test
  void noChaptersSingleChapter() throws Exception {
    Files.writeString(file, "只是一段没有章节标记的散文。\n继续写。\n", StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(1, parsed.chapters().size());
    assertEquals("仙路风云", parsed.chapters().get(0).title());
    assertTrue(parser.readChapter(file, parsed, 0).contains("散文"));
  }

  @Test
  void readChapterByOffset() throws Exception {
    Files.writeString(
        file,
        "第一章 开始\n正文一。\n第二章 发展\n正文二。\n第三章 结束\n正文三。\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    String c1 = parser.readChapter(file, parsed, 1);
    assertTrue(c1.contains("第二章 发展"));
    assertTrue(c1.contains("正文二"));
    assertFalse(c1.contains("第三章"));
    String c2 = parser.readChapter(file, parsed, 2);
    assertTrue(c2.contains("第三章 结束"));
  }

  @Test
  void frontMatterMergedIntoFirstChapter() throws Exception {
    Files.writeString(
        file,
        "书名：仙路风云\n作者：佚名\n第一章 开篇\n正文。\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(1, parsed.chapters().size());
    String c0 = parser.readChapter(file, parsed, 0);
    assertTrue(c0.contains("书名：仙路风云"));
    assertTrue(c0.contains("第一章 开篇"));
  }

  @Test
  void blankLinesCompressed() throws Exception {
    Files.writeString(
        file,
        "第一章 开始\n\n\n\n正文一。\n\n\n\n第二章 继续\n正文二。\n\n\n",
        StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    String c0 = parser.readChapter(file, parsed, 0);
    assertTrue(c0.contains("\n\n正文一"), "连续空行应压缩为单个空行");
    assertFalse(c0.contains("\n\n\n"));
  }

  @Test
  void indexOutOfRangeRejected() throws Exception {
    Files.writeString(file, "第一章 唯一\n正文。\n", StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertThrows(BizException.class, () -> parser.readChapter(file, parsed, 5));
    assertThrows(BizException.class, () -> parser.readChapter(file, parsed, -1));
  }

  @Test
  void chapterCountCapped() throws Exception {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= TxtNovelParser.MAX_CHAPTERS + 100; i++) {
      sb.append("第").append(i).append("章 标题").append(i).append('\n');
    }
    Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    assertEquals(TxtNovelParser.MAX_CHAPTERS, parsed.chapters().size());
  }

  @Test
  void oversizedChapterTruncated() throws Exception {
    StringBuilder sb = new StringBuilder("第一章 超长\n");
    int fill = TxtNovelParser.MAX_CHAPTER_BYTES + 1024;
    sb.append("x".repeat(fill)).append('\n');
    Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    TxtNovelParser.ParsedTxt parsed = parser.parse(file);
    String content = parser.readChapter(file, parsed, 0);
    assertTrue(content.length() <= TxtNovelParser.MAX_CHAPTER_BYTES + 64);
  }

  @Test
  void supportsExtension() {
    assertTrue(parser.supports("book.txt"));
    assertTrue(parser.supports("BOOK.TXT"));
    assertFalse(parser.supports("book.epub"));
    assertFalse(parser.supports(null));
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
