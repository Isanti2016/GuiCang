package com.guicang.nas.module.file;

import com.guicang.nas.infra.storage.StorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 孤儿分片清理：每天 03:30 清理 .guicang-tmp/chunks 下超过 24 小时未更新的分片目录（中断的上传）。 */
@Component
public class ChunkPurgeJob {

  private static final Logger log = LoggerFactory.getLogger(ChunkPurgeJob.class);
  private static final Duration MAX_AGE = Duration.ofHours(24);

  private final StorageService storageService;

  public ChunkPurgeJob(StorageService storageService) {
    this.storageService = storageService;
  }

  @Scheduled(cron = "0 30 3 * * ?")
  public void purge() {
    Path chunksRoot = storageService.root().resolve(".guicang-tmp/chunks");
    if (!Files.isDirectory(chunksRoot)) {
      return;
    }
    Instant deadline = Instant.now().minus(MAX_AGE);
    try (var stream = Files.list(chunksRoot)) {
      stream.forEach(
          dir -> {
            if (!Files.isDirectory(dir)) {
              return;
            }
            try {
              if (Files.getLastModifiedTime(dir).toInstant().isBefore(deadline)) {
                deleteRecursively(dir);
                log.info("已清理过期分片目录: {}", dir.getFileName());
              }
            } catch (IOException e) {
              log.warn("清理分片目录失败 {}: {}", dir, e.getMessage());
            }
          });
    } catch (IOException e) {
      log.warn("扫描分片目录失败: {}", e.getMessage());
    }
  }

  private void deleteRecursively(Path dir) throws IOException {
    try (var stream = Files.walk(dir)) {
      stream
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
              });
    }
  }
}
