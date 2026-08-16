package com.guicang.nas.infra.thumbnail;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 缩略图服务实现：Thumbnailator 生成 256px JPEG（quality 0.8），存 {@code <thumbs-dir>/<sha1>.jpg}。
 *
 * <p>懒生成：首次访问时生成并落盘缓存，后续直接读缓存；缓存键含文件指纹，原文件变化自动失效。
 */
@Service
public class ThumbnailServiceImpl implements ThumbnailService {

  private static final Logger log = LoggerFactory.getLogger(ThumbnailServiceImpl.class);
  private static final int THUMB_SIZE = 256;
  private static final String EXT = "jpg";

  private final Path thumbsDir;

  public ThumbnailServiceImpl(@Value("${guicang.thumbnails-dir:./data/thumbs}") String thumbsDir) {
    this.thumbsDir = Path.of(thumbsDir).toAbsolutePath().normalize();
  }

  @Override
  public Path thumbnail(Path sourceFile, String cacheKey) {
    try {
      Files.createDirectories(thumbsDir);
    } catch (IOException e) {
      throw new BizException("缩略图目录不可用");
    }
    Path thumb = thumbsDir.resolve(sha1(cacheKey) + "." + EXT);
    if (Files.exists(thumb)) {
      return thumb;
    }
    try {
      Thumbnails.of(sourceFile.toFile())
          .size(THUMB_SIZE, THUMB_SIZE)
          .outputFormat(EXT)
          .outputQuality(0.8)
          .toFile(thumb.toFile());
      return thumb;
    } catch (IOException e) {
      log.warn("缩略图生成失败: {} ({})", sourceFile, e.getMessage());
      throw new BizException("缩略图生成失败（文件可能已损坏或非图片）");
    }
  }

  private static String sha1(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 不可用", e);
    }
  }
}
