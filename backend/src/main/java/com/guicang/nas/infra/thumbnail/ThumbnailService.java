package com.guicang.nas.infra.thumbnail;

import java.nio.file.Path;

/** 缩略图服务：图片/视频生成 256px JPEG 缩略图并缓存到 thumbs 目录。 */
public interface ThumbnailService {

  /**
   * 生成（或取缓存）图片缩略图（Thumbnailator）。
   *
   * @param sourceFile 原图路径（已校验）
   * @param cacheKey 缓存键（含路径/大小/修改时间的指纹，文件变化自动失效）
   * @return 缩略图文件路径
   */
  Path thumbnail(Path sourceFile, String cacheKey);

  /**
   * 生成（或取缓存）视频封面（ffmpeg -ss 1s 抽帧）。
   *
   * @param sourceFile 视频路径（已校验）
   * @param cacheKey 缓存键（含路径/大小/修改时间的指纹）
   * @return 封面文件路径
   */
  Path videoThumbnail(Path sourceFile, String cacheKey);
}
