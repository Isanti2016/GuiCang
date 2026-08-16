package com.guicang.nas.infra.storage;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 存储路径工具：基于存储根解析并做越权校验（防目录穿越与符号链接逃逸）。
 *
 * <p>规则：拒绝空/绝对路径/含 {@code ..} 的路径；解析后必须位于存储根内； 存在时用 toRealPath 校验真实路径不逃逸根目录（覆盖中间 symlink）。
 */
public final class PathUtils {

  private PathUtils() {}

  /** 规范化 + 越权校验，返回存储根下的绝对路径（空串/空白视为根目录 "."）。 */
  public static Path resolve(Path root, String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      relativePath = ".";
    }
    if (relativePath.startsWith("/") || relativePath.matches("^[A-Za-z]:.*")) {
      throw new BizException("仅支持相对路径");
    }
    if (relativePath.contains("..")) {
      throw new BizException("非法路径（禁止 ..）");
    }
    Path rootAbs = root.toAbsolutePath().normalize();
    Path resolved = rootAbs.resolve(relativePath).normalize();
    if (!resolved.startsWith(rootAbs)) {
      throw new BizException("路径越界");
    }
    return resolved;
  }

  /** 校验真实路径不逃逸存储根（symlink 检测）；路径不存在时跳过。 */
  public static void checkRealPath(Path root, Path resolved) {
    if (!Files.exists(resolved)) {
      return;
    }
    try {
      Path rootReal = root.toRealPath();
      Path resolvedReal = resolved.toRealPath();
      if (!resolvedReal.startsWith(rootReal)) {
        throw new BizException("路径越界（符号链接逃逸）");
      }
    } catch (IOException e) {
      throw new BizException("路径解析失败");
    }
  }
}
