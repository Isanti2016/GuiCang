package com.guicang.nas.module.camera;

import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.file.FileIndexService;
import com.guicang.nas.module.setting.SysSetting;
import com.guicang.nas.module.setting.SysSettingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 监控录像归档任务：每 5 分钟扫描接收目录，将视频文件按「摄像头名/日期」归档到存储根
 * {@code cameras/archive} 下，并同步文件索引（支持 Web 端流式播放）。
 *
 * <p>目录约定：
 *
 * <ul>
 *   <li>接收目录（系统设置 camera.receive-dir，留空 = 存储根/cameras/incoming）：子目录名即
 *       摄像头名；直接放在根下的视频归入 default 摄像头。
 *   <li>归档目录（固定 存储根/cameras/archive）：{摄像头}/{yyyy-MM-dd}/{文件名}，必须位于存储根
 *       内以便索引与播放。
 * </ul>
 *
 * <p>摄像头名自动注册到 camera 表（前端可补位置备注）。日期优先取文件名内嵌 8 位数字
 * （20xxxxxx），否则取文件修改时间。
 */
@Component
public class CameraArchiveJob {

  private static final Logger log = LoggerFactory.getLogger(CameraArchiveJob.class);

  /** 归档目录相对存储根的固定前缀。 */
  public static final String ARCHIVE_PREFIX = "cameras/archive";

  private static final String DEFAULT_CAMERA = "default";

  private static final Set<String> VIDEO_EXT =
      Set.of("mp4", "mov", "avi", "mkv", "webm", "m4v", "flv", "ts", "m2ts", "mts");

  /** 文件名内嵌日期：CAM01_20260831_120000.mp4 → 2026-08-31。 */
  static final Pattern DATE_IN_NAME = Pattern.compile("(20\\d{2})(\\d{2})(\\d{2})");

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** 摄像头名安全字符白名单（字母/数字/下划线/连字符/中文）。 */
  private static final Pattern NAME_SAFE = Pattern.compile("[^A-Za-z0-9_\\-\\u4e00-\\u9fa5]");

  private final StorageService storageService;
  private final FileIndexService fileIndexService;
  private final CameraService cameraService;
  private final SysSettingService sysSettingService;

  public CameraArchiveJob(
      StorageService storageService,
      FileIndexService fileIndexService,
      CameraService cameraService,
      SysSettingService sysSettingService) {
    this.storageService = storageService;
    this.fileIndexService = fileIndexService;
    this.cameraService = cameraService;
    this.sysSettingService = sysSettingService;
  }

  /** 每 5 分钟扫描一次接收目录。 */
  @Scheduled(cron = "0 */5 * * * ?")
  public void archiveIncoming() {
    try {
      Path root = storageService.root();
      Path receiveDir = resolveReceiveDir(root);
      if (!Files.isDirectory(receiveDir)) {
        Files.createDirectories(receiveDir);
        log.info("监控接收目录已创建: {}", receiveDir);
        return;
      }
      int archived = 0;
      // 1. 子目录 = 摄像头
      try (Stream<Path> stream = Files.list(receiveDir)) {
        List<Path> cameraDirs = stream.filter(Files::isDirectory).toList();
        for (Path camDir : cameraDirs) {
          String camera = sanitize(camDir.getFileName().toString());
          archived += archiveDir(camDir, camera, root);
          removeIfEmpty(camDir);
        }
      }
      // 2. 根下直接放置的视频 → default 摄像头
      try (Stream<Path> stream = Files.list(receiveDir)) {
        List<Path> loose =
            stream
                .filter(p -> Files.isRegularFile(p) && isVideo(p.getFileName().toString()))
                .toList();
        for (Path file : loose) {
          archived += archiveFile(file, DEFAULT_CAMERA, root);
        }
      }
      if (archived > 0) {
        log.info("监控录像归档完成，共 {} 个文件", archived);
      }
    } catch (Exception e) {
      log.warn("监控录像归档失败: {}", e.getMessage());
    }
  }

  /** 归档某个摄像头目录下的全部视频文件。 */
  private int archiveDir(Path camDir, String camera, Path root) {
    int count = 0;
    try (Stream<Path> stream = Files.list(camDir)) {
      List<Path> videos =
          stream
              .filter(p -> Files.isRegularFile(p) && isVideo(p.getFileName().toString()))
              .toList();
      for (Path file : videos) {
        count += archiveFile(file, camera, root);
      }
    } catch (IOException e) {
      log.warn("读取摄像头目录失败 {}: {}", camDir, e.getMessage());
    }
    return count;
  }

  /** 归档单个视频文件：复制到归档目录（跨卷安全）并建索引，成功后再删源文件。 */
  private int archiveFile(Path file, String camera, Path root) {
    try {
      String name = file.getFileName().toString();
      long mtime = Files.getLastModifiedTime(file).toMillis();
      String date = dateOf(name, mtime);
      Path target = uniqueTarget(root, sanitize(camera), date, name);
      Files.createDirectories(target.getParent());
      Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
      Files.deleteIfExists(file);
      String rel =
          ARCHIVE_PREFIX + "/" + sanitize(camera) + "/" + date + "/" + target.getFileName();
      fileIndexService.upsert(
          rel, target.getFileName().toString(), "video", Files.size(target), mtime, "system");
      cameraService.autoRegister(sanitize(camera));
      log.info("监控录像归档: {} → {}", name, rel);
      return 1;
    } catch (Exception e) {
      log.warn("归档文件失败 {}: {}", file, e.getMessage());
      return 0;
    }
  }

  /** 目标路径去重：已存在则追加 _1/_2 后缀。 */
  private Path uniqueTarget(Path root, String camera, String date, String name) {
    String base = name;
    String ext = "";
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      base = name.substring(0, dot);
      ext = name.substring(dot);
    }
    Path target = root.resolve(ARCHIVE_PREFIX).resolve(camera).resolve(date).resolve(name);
    int n = 1;
    while (Files.exists(target)) {
      target =
          root.resolve(ARCHIVE_PREFIX)
              .resolve(camera)
              .resolve(date)
              .resolve(base + "_" + n + ext);
      n++;
    }
    return target;
  }

  private Path resolveReceiveDir(Path root) {
    String configured = sysSettingService.all().getOrDefault(SysSetting.CAMERA_RECEIVE_DIR.key(), "");
    if (configured == null || configured.isBlank()) {
      return root.resolve("cameras/incoming");
    }
    return Path.of(configured.trim());
  }

  private void removeIfEmpty(Path dir) {
    try (Stream<Path> stream = Files.list(dir)) {
      if (stream.findAny().isEmpty()) {
        Files.deleteIfExists(dir);
      }
    } catch (IOException ignored) {
      // 目录清理尽力而为
    }
  }

  /** 解析日期：文件名内嵌 20xxxxxx 优先，否则取 mtime 对应日期。 */
  static String dateOf(String name, long mtimeMs) {
    Matcher matcher = DATE_IN_NAME.matcher(name == null ? "" : name);
    if (matcher.find()) {
      try {
        LocalDate date =
            LocalDate.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
        return DATE_FMT.format(date);
      } catch (DateTimeException | NumberFormatException ignored) {
        // 非法日期回退到 mtime
      }
    }
    return Instant.ofEpochMilli(mtimeMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DATE_FMT);
  }

  /** 摄像头名清洗：仅保留安全字符，防路径穿越；空/非法回退 default。 */
  static String sanitize(String name) {
    if (name == null) {
      return DEFAULT_CAMERA;
    }
    String cleaned = NAME_SAFE.matcher(name.trim()).replaceAll("_");
    cleaned = cleaned.replaceAll("_+", "_").replaceAll("^_|_$", "");
    if (cleaned.isEmpty() || cleaned.equals("..") || cleaned.equals(".")) {
      return DEFAULT_CAMERA;
    }
    return cleaned;
  }

  /** 扩展名是否为受支持的视频格式。 */
  static boolean isVideoExt(String ext) {
    return ext != null && VIDEO_EXT.contains(ext.toLowerCase(Locale.ROOT));
  }

  private static boolean isVideo(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0) {
      return false;
    }
    return isVideoExt(name.substring(dot + 1));
  }
}
