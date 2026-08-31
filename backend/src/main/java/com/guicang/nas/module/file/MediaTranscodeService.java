package com.guicang.nas.module.file;

import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.file.dto.TranscodeStatusVO;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 视频转码为浏览器兼容版（一键转码）：
 * 输出 {@code 原名.compat.mp4}，视频流 copy 不重编码（快），音轨统一转 aac 192k + faststart，
 * 供 eac3/ac3/dts/truehd 等浏览器不支持的音轨直接播放。
 */
@Service
public class MediaTranscodeService {

  private static final Logger log = LoggerFactory.getLogger(MediaTranscodeService.class);

  private final StorageService storageService;
  private final MediaMetadataCacheMapper cacheMapper;

  /** path -> 任务状态（内存态；重启后任务丢失，但 compat 文件已落盘可复用）。 */
  private final Map<String, TranscodeTask> tasks = new ConcurrentHashMap<>();

  public MediaTranscodeService(
      StorageService storageService, MediaMetadataCacheMapper cacheMapper) {
    this.storageService = storageService;
    this.cacheMapper = cacheMapper;
  }

  /** 任务状态（可变）。 */
  static final class TranscodeTask {
    volatile String status = "RUNNING";
    volatile int progress = 0;
    volatile String message = "";
  }

  /** 兼容版相对路径：原名去掉扩展名 + .compat.mp4。 */
  public static String compatName(String relativePath) {
    int dot = relativePath.lastIndexOf('.');
    return (dot < 0 ? relativePath : relativePath.substring(0, dot)) + ".compat.mp4";
  }

  /** 启动转码；若 compat 文件已存在或任务运行中则直接返回当前状态（幂等）。 */
  public TranscodeStatusVO start(String relativePath) {
    String outRel = compatName(relativePath);
    Path outAbs = storageService.resolveForWrite(outRel);
    if (Files.exists(outAbs)) {
      return new TranscodeStatusVO("DONE", 100, "兼容版已存在", outRel);
    }
    TranscodeTask existing = tasks.get(relativePath);
    if (existing != null && "RUNNING".equals(existing.status)) {
      return toVO(existing, outRel);
    }
    TranscodeTask task = new TranscodeTask();
    tasks.put(relativePath, task);
    Thread worker =
        new Thread(() -> runTranscode(relativePath, outRel, task), "transcode-" + relativePath);
    worker.setDaemon(true);
    worker.start();
    return toVO(task, outRel);
  }

  /** 查询转码状态（幂等；compat 已存在则直接 DONE）。 */
  public TranscodeStatusVO status(String relativePath) {
    String outRel = compatName(relativePath);
    Path outAbs = storageService.resolveForWrite(outRel);
    if (Files.exists(outAbs)) {
      return new TranscodeStatusVO("DONE", 100, "兼容版已存在", outRel);
    }
    TranscodeTask task = tasks.get(relativePath);
    if (task == null) {
      return new TranscodeStatusVO("IDLE", 0, "未开始", outRel);
    }
    return toVO(task, outRel);
  }

  private TranscodeStatusVO toVO(TranscodeTask task, String outRel) {
    return new TranscodeStatusVO(task.status, task.progress, task.message, outRel);
  }

  private void runTranscode(String relativePath, String outRel, TranscodeTask task) {
    Path outAbs = null;
    Path partAbs = null;
    try {
      Path src = storageService.resolveFile(relativePath);
      outAbs = storageService.resolveForWrite(outRel);
      // 先写 .part 临时文件，成功后原子改名，避免 status() 看到半成品误判 DONE
      partAbs = storageService.resolveForWrite(outRel + ".part");
      Files.deleteIfExists(partAbs);
      long durationMs = lookupDurationMs(relativePath);

      List<String> cmd =
          List.of(
              "ffmpeg",
              "-y",
              "-i",
              src.toString(),
              "-map",
              "0:v:0",
              "-map",
              "0:a:0?",
              "-c:v",
              "copy",
              "-c:a",
              "aac",
              "-b:a",
              "192k",
              "-movflags",
              "+faststart",
              "-f",
              "mp4",
              "-progress",
              "pipe:1",
              "-nostats",
              partAbs.toString());
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(false);
      Process p = pb.start();

      // 读 stdout 的 out_time_ms 估算进度
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("out_time_ms=")) {
            try {
              long ms = Long.parseLong(line.substring("out_time_ms=".length()).trim());
              if (durationMs > 0) {
                task.progress = (int) Math.min(99, ms * 100 / durationMs);
              }
            } catch (NumberFormatException ignored) {
              // 忽略非数字行
            }
          }
        }
      }

      int exit = p.waitFor();
      if (exit == 0 && Files.exists(partAbs) && Files.size(partAbs) > 0) {
        // 转码完成：原子改名（同卷 move），compat 文件才算真正可用
        Files.move(partAbs, outAbs, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        task.status = "DONE";
        task.progress = 100;
        log.info("转码完成: {} -> {}", relativePath, outRel);
      } else {
        task.status = "FAILED";
        task.message = "ffmpeg 退出码 " + exit;
        log.warn("转码失败: {} exit={}", relativePath, exit);
        Files.deleteIfExists(partAbs);
      }
    } catch (Exception e) {
      task.status = "FAILED";
      task.message = e.getMessage();
      log.warn("转码异常: {}", relativePath, e);
      if (partAbs != null) {
        try {
          Files.deleteIfExists(partAbs);
        } catch (Exception ignored) {
          // 忽略清理失败
        }
      }
    }
  }

  /** 从缓存表取时长（毫秒）；取不到返回 0（进度仅显示进行中）。 */
  private long lookupDurationMs(String relativePath) {
    try {
      MediaMetadataCache row = cacheMapper.selectById(relativePath);
      if (row != null && row.getDurationSec() != null && row.getDurationSec() > 0) {
        return row.getDurationSec() * 1000L;
      }
    } catch (Exception e) {
      log.debug("读取时长缓存失败: {}", e.getMessage());
    }
    return 0;
  }
}
