package com.guicang.nas.module.sync;

import com.guicang.nas.infra.storage.FileTypeUtils;
import java.nio.file.Path;

/**
 * 按文件类型归档：image / video / note / other。
 *
 * <p>分类复用 {@link FileTypeUtils#kind}，目录不参与整理（仅文件）。
 */
public class KindOrganizeRule implements OrganizeRule {

  @Override
  public String subPath(Path file, String name) {
    String kind = FileTypeUtils.kind(name);
    return switch (kind) {
      case "image", "video", "note" -> kind;
      default -> "other";
    };
  }
}
