package com.guicang.nas.infra.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 文件类型工具：Content-Type 推断与危险扩展名拦截。 */
public final class FileTypeUtils {

  private static final Map<String, String> MIME =
      Map.ofEntries(
          Map.entry("txt", "text/plain; charset=utf-8"),
          Map.entry("md", "text/markdown; charset=utf-8"),
          Map.entry("markdown", "text/markdown; charset=utf-8"),
          Map.entry("html", "text/html; charset=utf-8"),
          Map.entry("htm", "text/html; charset=utf-8"),
          Map.entry("css", "text/css"),
          Map.entry("js", "application/javascript"),
          Map.entry("json", "application/json"),
          Map.entry("xml", "application/xml"),
          Map.entry("csv", "text/csv; charset=utf-8"),
          Map.entry("jpg", "image/jpeg"),
          Map.entry("jpeg", "image/jpeg"),
          Map.entry("png", "image/png"),
          Map.entry("gif", "image/gif"),
          Map.entry("webp", "image/webp"),
          Map.entry("bmp", "image/bmp"),
          Map.entry("svg", "image/svg+xml"),
          Map.entry("mp4", "video/mp4"),
          Map.entry("m4v", "video/x-m4v"),
          Map.entry("webm", "video/webm"),
          Map.entry("mov", "video/quicktime"),
          Map.entry("avi", "video/x-msvideo"),
          Map.entry("mkv", "video/x-matroska"),
          Map.entry("mp3", "audio/mpeg"),
          Map.entry("wav", "audio/wav"),
          Map.entry("flac", "audio/flac"),
          Map.entry("ogg", "audio/ogg"),
          Map.entry("pdf", "application/pdf"),
          Map.entry("zip", "application/zip"),
          Map.entry("gz", "application/gzip"),
          Map.entry("tar", "application/x-tar"),
          Map.entry("7z", "application/x-7z-compressed"),
          Map.entry("rar", "application/vnd.rar"));

  private FileTypeUtils() {}

  /** 按扩展名推断 Content-Type；未知返回 application/octet-stream。 */
  public static String contentType(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return "application/octet-stream";
    }
    String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
    return MIME.getOrDefault(ext, "application/octet-stream");
  }

  /** 扩展名是否命中危险黑名单。 */
  public static boolean isBlocked(String name, Set<String> blockedExtensions) {
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return false;
    }
    String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
    return blockedExtensions.contains(ext);
  }
}
