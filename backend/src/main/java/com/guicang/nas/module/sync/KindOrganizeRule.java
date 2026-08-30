package com.guicang.nas.module.sync;

import com.guicang.nas.infra.storage.FileTypeUtils;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 按文件类型归档：image / video / audio / document / note / other。
 *
 * <p>图片与视频复用 {@link FileTypeUtils#kind} 判定；音频（mp3/wav/flac/ogg/m4a/aac）与 文档
 * （pdf/doc/docx/xls/xlsx/ppt/pptx/zip/rar/7z/tar/gz/csv）在扩展名层面独立归类； md/txt 保持 note；其余归
 * other。目录不参与整理（仅文件）。
 */
public class KindOrganizeRule implements OrganizeRule {

  private static final Set<String> AUDIO_EXT = Set.of("mp3", "wav", "flac", "ogg", "m4a", "aac");
  private static final Set<String> DOC_EXT =
      Set.of(
          "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "rar", "7z", "tar", "gz",
          "csv");

  @Override
  public String subPath(Path file, String name) {
    String ext = extOf(name);
    if (AUDIO_EXT.contains(ext)) {
      return "audio";
    }
    if (DOC_EXT.contains(ext)) {
      return "document";
    }
    String kind = FileTypeUtils.kind(name);
    return switch (kind) {
      case "image", "video", "note" -> kind;
      default -> "other";
    };
  }

  private String extOf(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return "";
    }
    return name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
