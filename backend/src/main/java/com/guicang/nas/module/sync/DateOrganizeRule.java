package com.guicang.nas.module.sync;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 按文件修改时间归档：date_year → YYYY；date_month → YYYY/MM；date_day → YYYY/MM/DD。
 *
 * <p>时间口径为文件 mtime（MVP 约定，不读取照片 EXIF）。
 */
public class DateOrganizeRule implements OrganizeRule {

  private final String level;
  private final ZoneId zone;

  public DateOrganizeRule(String level) {
    this(level, ZoneId.systemDefault());
  }

  public DateOrganizeRule(String level, ZoneId zone) {
    this.level = level == null ? "date_month" : level;
    this.zone = zone;
  }

  @Override
  public String subPath(Path file, String name) {
    long mtime;
    try {
      mtime = java.nio.file.Files.getLastModifiedTime(file).toMillis();
    } catch (Exception e) {
      mtime = System.currentTimeMillis();
    }
    LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(mtime), zone);
    return switch (level) {
      case "date_year" -> String.valueOf(dt.getYear());
      case "date_day" ->
          dt.getYear()
              + "/"
              + dt.format(DateTimeFormatter.ofPattern("MM"))
              + "/"
              + dt.format(DateTimeFormatter.ofPattern("dd"));
      default -> dt.getYear() + "/" + dt.format(DateTimeFormatter.ofPattern("MM"));
    };
  }
}
