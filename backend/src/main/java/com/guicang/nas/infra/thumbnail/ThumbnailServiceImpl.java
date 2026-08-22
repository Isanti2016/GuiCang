package com.guicang.nas.infra.thumbnail;

import com.guicang.nas.common.BizException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 缩略图服务实现：图片用 Thumbnailator、视频用 ffmpeg 抽帧，均输出 256px JPEG， 存 {@code <thumbs-dir>/<sha1>.jpg}。
 *
 * <p>懒生成：首次访问时生成并落盘缓存，后续直接读缓存；缓存键含文件指纹，原文件变化自动失效。
 */
@Service
public class ThumbnailServiceImpl implements ThumbnailService {

  private static final Logger log = LoggerFactory.getLogger(ThumbnailServiceImpl.class);
  private static final int THUMB_SIZE = 256;
  private static final String EXT = "jpg";
  private static final Duration FFMPEG_TIMEOUT = Duration.ofSeconds(30);

  private final Path thumbsDir;
  private final String ffmpegPath;

  public ThumbnailServiceImpl(
      @Value("${guicang.thumbnails-dir:./data/thumbs}") String thumbsDir,
      @Value("${guicang.ffmpeg.path:ffmpeg}") String ffmpegPath) {
    this.thumbsDir = Path.of(thumbsDir).toAbsolutePath().normalize();
    this.ffmpegPath = ffmpegPath;
  }

  /**
   * 生成（或取缓存）图片缩略图（Thumbnailator）。
   *
   * @param sourceFile 原图路径（已校验）
   * @param cacheKey 缓存键（含路径/大小/修改时间的指纹，文件变化自动失效）
   * @return 缩略图文件路径
   */
  @Override
  public Path thumbnail(Path sourceFile, String cacheKey) {
    Path thumb = thumbPath(cacheKey);
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
      log.warn("图片缩略图生成失败: {} ({})", sourceFile, e.getMessage());
      throw new BizException("缩略图生成失败（文件可能已损坏或非图片）");
    }
  }

  /**
   * 生成（或取缓存）视频封面（ffmpeg -ss 1s 抽帧）。
   *
   * @param sourceFile 视频路径（已校验）
   * @param cacheKey 缓存键（含路径/大小/修改时间的指纹）
   * @return 封面文件路径
   */
  @Override
  public Path videoThumbnail(Path sourceFile, String cacheKey) {
    Path thumb = thumbPath(cacheKey);
    if (Files.exists(thumb)) {
      return thumb;
    }
    try {
      Process process =
          new ProcessBuilder(
                  ffmpegPath,
                  "-ss",
                  "00:00:01",
                  "-i",
                  sourceFile.toString(),
                  "-vframes",
                  "1",
                  "-q:v",
                  "2",
                  "-y",
                  thumb.toString())
              .redirectErrorStream(true)
              .start();
      process.getInputStream().readAllBytes();
      if (!process.waitFor(FFMPEG_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
          || process.exitValue() != 0) {
        process.destroyForcibly();
        throw new BizException("视频抽帧失败（ffmpeg 不可用或文件损坏）");
      }
      return thumb;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BizException("视频抽帧失败（ffmpeg 不可用或文件损坏）");
    }
  }

  private Path thumbPath(String cacheKey) {
    try {
      Files.createDirectories(thumbsDir);
    } catch (IOException e) {
      throw new BizException("缩略图目录不可用");
    }
    return thumbsDir.resolve(sha1(cacheKey) + "." + EXT);
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
