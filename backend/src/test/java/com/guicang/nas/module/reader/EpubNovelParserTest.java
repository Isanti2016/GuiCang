package com.guicang.nas.module.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** EPUB 小说解析测试：内存构造最小 epub 验证解析、去标签、实体、安全拒绝。 */
class EpubNovelParserTest {

  @TempDir Path dir;

  private final EpubNovelParser parser = new EpubNovelParser();

  private Path file;

  @BeforeEach
  void setUp() {
    file = dir.resolve("剑来.epub");
  }

  @Test
  void parseMinimalEpub() throws Exception {
    writeEpub(
        Map.of(
            "META-INF/container.xml",
            "<?xml version=\"1.0\"?><container><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>",
            "OEBPS/content.opf",
            opf(
                "剑来",
                "烽火戏诸侯",
                """
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                """,
                "<itemref idref=\"ch1\"/><itemref idref=\"ch2\"/>"),
            "OEBPS/ch1.xhtml",
            "<html><head><title>c1</title></head><body><h1>第一章 陈平安</h1><p>泥瓶巷的清晨&amp;nbsp;很安静。</p></body></html>",
            "OEBPS/ch2.xhtml",
            "<html><body><p>第二章 教书先生</p><p>他说&lt;别怕&gt;。</p></body></html>"));

    EpubNovelParser.ParsedEpub parsed = parser.parse(file);
    assertEquals("剑来", parsed.title());
    assertEquals("烽火戏诸侯", parsed.author());
    assertEquals(2, parsed.chapters().size());
    assertEquals("第一章 陈平安", parsed.chapters().get(0).title());
    assertTrue(parsed.chapters().get(0).content().contains("泥瓶巷的清晨 很安静"));
    assertTrue(parsed.chapters().get(1).content().contains("他说<别怕>"));
  }

  @Test
  void missingContainerRejected() throws Exception {
    writeEpub(Map.of("misc/file.txt", "hello"));
    BizException ex = assertThrows(BizException.class, () -> parser.parse(file));
    assertTrue(ex.getMessage().contains("container"));
  }

  @Test
  void corruptedZipRejected() throws Exception {
    Files.write(file, "this is not a zip file at all".getBytes(StandardCharsets.UTF_8));
    assertThrows(IOException.class, () -> parser.parse(file));
  }

  @Test
  void zipSlipPathRejected() throws Exception {
    writeEpub(
        Map.of(
            "META-INF/container.xml",
            "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\"/></rootfiles></container>",
            "OEBPS/content.opf",
            opf("x", null, "<item id=\"c1\" href=\"../../../etc/passwd\"/>", "<itemref idref=\"c1\"/>")));
    assertThrows(BizException.class, () -> parser.parse(file));
  }

  @Test
  void emptyChapterSkipped() throws Exception {
    writeEpub(
        Map.of(
            "META-INF/container.xml",
            "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\"/></rootfiles></container>",
            "OEBPS/content.opf",
            opf("x", null, "<item id=\"c1\" href=\"c1.xhtml\"/>", "<itemref idref=\"c1\"/>"),
            "OEBPS/c1.xhtml",
            "<html><body><h1>标题</h1><p></p><p> </p></body></html>"));
    assertThrows(BizException.class, () -> parser.parse(file), "全空正文应报无文本章节");
  }

  @Test
  void headingExtractedFromH1() {
    EpubNovelParser.EpubText text =
        EpubNovelParser.htmlToText("<h2>  第二章 风起  </h2><p>正文。</p>");
    assertEquals("第二章 风起", text.heading());
    assertTrue(text.body().contains("正文"));
    assertFalse(text.body().contains("风起"), "正文首行与标题重复应去掉");
  }

  @Test
  void entitiesDecoded() {
    String text = EpubNovelParser.decodeEntities("&lt;a&gt; &amp; &#39;x&#39; &#x4E2D; &nbsp;");
    assertEquals("<a> & 'x' 中  ", text);
  }

  @Test
  void scriptStyleStripped() {
    EpubNovelParser.EpubText text =
        EpubNovelParser.htmlToText(
            "<style>.x{color:red}</style><script>alert(1)</script><p>正文</p>");
    assertTrue(text.body().contains("正文"));
    assertFalse(text.body().contains("color"));
    assertFalse(text.body().contains("alert"));
  }

  @Test
  void manifestAndSpineParsed() {
    String opf =
        opf(
            "t",
            null,
            "<item id=\"c1\" href=\"a.xhtml\"/><item id=\"cover\" href=\"cover.xhtml\"/>",
            "<itemref idref=\"cover\"/><itemref idref=\"c1\"/>");
    Map<String, String> manifest = EpubNovelParser.parseManifest(opf);
    assertEquals("a.xhtml", manifest.get("c1"));
    assertEquals("cover.xhtml", manifest.get("cover"));
    var spine = EpubNovelParser.parseSpine(opf);
    assertEquals(2, spine.size());
    assertEquals("cover", spine.get(0));
    assertEquals("c1", spine.get(1));
  }

  @Test
  void resolveItemPath() {
    assertNull(EpubNovelParser.resolveItemPath("", "../evil.txt"));
    assertNull(EpubNovelParser.resolveItemPath("OEBPS/", "../../../etc/passwd"));
    assertEquals("OEBPS/text/ch1.xhtml", EpubNovelParser.resolveItemPath("OEBPS/", "text/ch1.xhtml"));
    assertEquals("OEBPS/ch1.xhtml", EpubNovelParser.resolveItemPath("OEBPS/", "../OEBPS/ch1.xhtml"));
  }

  @Test
  void supportsExtension() {
    assertTrue(parser.supports("book.epub"));
    assertTrue(parser.supports("BOOK.EPUB"));
    assertFalse(parser.supports("book.txt"));
    assertFalse(parser.supports(null));
  }

  /** 构造一个最小 epub（zip）。 */
  private void writeEpub(Map<String, String> entries) throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      for (Map.Entry<String, String> e : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(e.getKey()));
        out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
  }

  private String opf(String title, String creator, String items, String spine) {
    String creatorTag = creator == null ? "" : "<dc:creator>" + creator + "</dc:creator>";
    return "<?xml version=\"1.0\"?><package><metadata><dc:title>"
        + title
        + "</dc:title>"
        + creatorTag
        + "</metadata><manifest>"
        + items
        + "</manifest><spine>"
        + spine
        + "</spine></package>";
  }
}
