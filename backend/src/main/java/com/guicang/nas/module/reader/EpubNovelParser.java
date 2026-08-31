package com.guicang.nas.module.reader;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

/**
 * EPUB 小说解析器（EPUB 2/3 通用路径）：container.xml → OPF（元数据 + manifest + spine）→ 按 spine
 * 顺序读取 XHTML/HTML，去标签得纯文本。
 *
 * <p>仅解析文本章节，不加载内嵌图片；损坏 zip、缺少 container、Zip Slip 路径均拒绝。
 */
@Component
public class EpubNovelParser {

  /** 单个 spine 条目读取上限：8MB。 */
  static final int MAX_ITEM_BYTES = 8 * 1024 * 1024;

  /** spine 条目数上限。 */
  static final int MAX_ITEMS = 2000;

  private static final Pattern CONTAINER_ROOTFILE =
      Pattern.compile("<rootfile[^>]*full-path\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private static final Pattern OPF_TITLE =
      Pattern.compile("<dc:title[^>]*>(.*?)</dc:title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern OPF_CREATOR =
      Pattern.compile("<dc:creator[^>]*>(.*?)</dc:creator>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern OPF_ITEM =
      Pattern.compile("<item\\b[^>]*>", Pattern.CASE_INSENSITIVE);

  private static final Pattern OPF_ITEMREF =
      Pattern.compile("<itemref\\b[^>]*>", Pattern.CASE_INSENSITIVE);

  private static final Pattern ATTR_ID =
      Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private static final Pattern ATTR_HREF =
      Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  private static final Pattern ATTR_IDREF =
      Pattern.compile("\\bidref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

  /** 章节解析结果。 */
  public record ParsedEpub(String title, String author, List<EpubChapter> chapters) {}

  /** EPUB 章节：标题 + 纯文本正文。 */
  public record EpubChapter(int index, String title, String content) {}

  /** 解析 EPUB 文件。 */
  public ParsedEpub parse(Path file) throws IOException {
    try (ZipFile zip = new ZipFile(file.toFile())) {
      ZipEntry container = zip.getEntry("META-INF/container.xml");
      if (container == null) {
        throw new BizException("不是有效的 EPUB 文件（缺少 META-INF/container.xml）");
      }
      String containerXml = readEntry(zip, container, 64 * 1024);
      Matcher rootfile = CONTAINER_ROOTFILE.matcher(containerXml);
      if (!rootfile.find()) {
        throw new BizException("EPUB 文件缺少 OPF 入口（container.xml 无 rootfile）");
      }
      String opfPath = rootfile.group(1).trim();
      ZipEntry opfEntry = zip.getEntry(opfPath);
      if (opfEntry == null) {
        throw new BizException("EPUB 文件缺少 OPF 包文档: " + opfPath);
      }
      String opf = readEntry(zip, opfEntry, 1024 * 1024);

      String title = extractText(OPF_TITLE, opf);
      String author = extractText(OPF_CREATOR, opf);
      Map<String, String> manifest = parseManifest(opf);
      List<String> spine = parseSpine(opf);
      String opfDir = opfPath.contains("/")
          ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1)
          : "";

      List<EpubChapter> chapters = new ArrayList<>();
      for (String idref : spine) {
        if (chapters.size() >= MAX_ITEMS) {
          break;
        }
        String href = manifest.get(idref);
        if (href == null || href.isBlank()) {
          continue;
        }
        String itemPath = resolveItemPath(opfDir, href);
        if (itemPath == null) {
          throw new BizException("EPUB 内容路径越界: " + href);
        }
        ZipEntry item = zip.getEntry(itemPath);
        if (item == null || item.isDirectory()) {
          continue;
        }
        String html = readEntry(zip, item, MAX_ITEM_BYTES);
        EpubText text = htmlToText(html);
        if (!text.hasContent()) {
          continue;
        }
        String chapterTitle =
            text.heading() != null ? text.heading() : "第 " + (chapters.size() + 1) + " 章";
        chapters.add(new EpubChapter(chapters.size(), chapterTitle, text.body()));
      }
      if (chapters.isEmpty()) {
        throw new BizException("EPUB 未解析出任何文本章节");
      }
      if (title == null || title.isBlank()) {
        title = file.getFileName().toString();
      }
      return new ParsedEpub(title, author, chapters);
    }
  }

  /** 当前解析支持的扩展名。 */
  public boolean supports(String fileName) {
    return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".epub");
  }

  /** 解析 manifest：id → href。 */
  static Map<String, String> parseManifest(String opf) {
    Map<String, String> map = new HashMap<>();
    Matcher itemMatcher = OPF_ITEM.matcher(opf);
    while (itemMatcher.find()) {
      String tag = itemMatcher.group();
      String id = attr(ATTR_ID, tag);
      String href = attr(ATTR_HREF, tag);
      if (id != null && href != null) {
        map.put(id, href);
      }
    }
    return map;
  }

  /** 解析 spine：按序的 idref 列表（忽略 linear="no" 之外的常规处理）。 */
  static List<String> parseSpine(String opf) {
    List<String> spine = new ArrayList<>();
    Matcher itemrefMatcher = OPF_ITEMREF.matcher(opf);
    while (itemrefMatcher.find()) {
      String idref = attr(ATTR_IDREF, itemrefMatcher.group());
      if (idref != null) {
        spine.add(idref);
      }
    }
    return spine;
  }

  /** 解析 item 路径：拼上 OPF 所在目录并防穿越（Zip Slip）。 */
  static String resolveItemPath(String opfDir, String href) {
    String combined = opfDir + href;
    String normalized = combined.replace('\\', '/');
    String[] parts = normalized.split("/");
    List<String> stack = new ArrayList<>();
    for (String part : parts) {
      if (part.isEmpty() || ".".equals(part)) {
        continue;
      }
      if ("..".equals(part)) {
        if (stack.isEmpty()) {
          return null;
        }
        stack.remove(stack.size() - 1);
      } else {
        stack.add(part);
      }
    }
    if (stack.isEmpty()) {
      return null;
    }
    return String.join("/", stack);
  }

  /** HTML → 文本：去 script/style、块级换行、去标签、解实体。 */
  static EpubText htmlToText(String html) {
    String s = html;
    s = s.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>", " ");
    // 提取标题：第一个 h1-h6 文本（必须在闭合标签被替换成换行之前）
    Matcher headingMatcher =
        Pattern.compile("(?is)<h([1-6])\\b[^>]*>(.*?)</h\\1\\s*>").matcher(s);
    String heading = null;
    if (headingMatcher.find()) {
      heading = decodeEntities(stripTags(headingMatcher.group(2))).trim();
      if (heading.isBlank()) {
        heading = null;
      }
    }
    s = s.replaceAll("(?i)</(p|div|h[1-6]|li|br|tr|section|article|blockquote|pre)\\s*>", "\n");
    s = stripTags(s);
    s = decodeEntities(s);
    s = s.replaceAll("\\u00A0", " ");
    s = s.replaceAll("[ \\t]+", " ");
    // 压缩连续空行
    s = s.replaceAll("\\n{3,}", "\n\n");
    String[] lines = s.split("\\n", -1);
    StringBuilder body = new StringBuilder(s.length());
    boolean pendingBlank = false;
    for (String line : lines) {
      String t = line.trim();
      if (t.isEmpty()) {
        pendingBlank = body.length() > 0;
        continue;
      }
      if (pendingBlank && body.length() > 0) {
        body.append('\n');
      }
      pendingBlank = false;
      body.append(t).append('\n');
    }
    int len = body.length();
    while (len > 0 && body.charAt(len - 1) == '\n') {
      len--;
    }
    body.setLength(len);
    // 正文首行若与标题重复则去掉（常见：正文第一个标题行）
    String bodyText = body.toString();
    if (heading != null && bodyText.startsWith(heading)) {
      bodyText = bodyText.substring(heading.length()).stripLeading();
    }
    return new EpubText(heading, bodyText);
  }

  /** 提取文本标签内容（OPF 元数据）。 */
  static String extractText(Pattern pattern, String opf) {
    Matcher m = pattern.matcher(opf);
    if (!m.find()) {
      return null;
    }
    String value = decodeEntities(stripTags(m.group(1))).trim();
    return value.isBlank() ? null : value;
  }

  /** 去除 HTML 标签。 */
  static String stripTags(String html) {
    return html.replaceAll("(?s)<[^>]+>", " ");
  }

  /** 解码常见 HTML 实体（含数字实体）。 */
  static String decodeEntities(String text) {
    if (text.indexOf('&') < 0) {
      return text;
    }
    String s =
        text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            .replace("&mdash;", "\u2014")
            .replace("&ndash;", "\u2013")
            .replace("&hellip;", "\u2026")
            .replace("&middot;", "\u00B7")
            .replace("&times;", "\u00D7");
    // 数字实体 &#NNN; / &#xHH;（Matcher.replaceAll(Function) 是 Java 9+ API）
    s = Pattern.compile("&#(\\d+);").matcher(s).replaceAll(m -> String.valueOf((char) Integer.parseInt(m.group(1))));
    s =
        Pattern.compile("&#x([0-9a-fA-F]+);")
            .matcher(s)
            .replaceAll(m -> String.valueOf((char) Integer.parseInt(m.group(1), 16)));
    return s;
  }

  private static String attr(Pattern pattern, String tag) {
    Matcher m = pattern.matcher(tag);
    return m.find() ? m.group(1).trim() : null;
  }

  private static String readEntry(ZipFile zip, ZipEntry entry, int maxBytes) throws IOException {
    long size = entry.getSize();
    if (size > maxBytes) {
      throw new BizException("EPUB 条目过大: " + entry.getName());
    }
    byte[] data;
    try (InputStream in = zip.getInputStream(entry)) {
      data = in.readNBytes(maxBytes + 1);
    }
    if (data.length > maxBytes) {
      throw new BizException("EPUB 条目过大: " + entry.getName());
    }
    Charset cs = StandardCharsets.UTF_8;
    if (data.length >= 3
        && (data[0] & 0xFF) == 0xEF
        && (data[1] & 0xFF) == 0xBB
        && (data[2] & 0xFF) == 0xBF) {
      return new String(data, 3, data.length - 3, cs);
    }
    return new String(data, cs);
  }

  /** HTML 转文本结果：可选标题 + 正文。 */
  record EpubText(String heading, String body) {
    boolean hasContent() {
      return !body.isBlank();
    }
  }
}
