package com.guicang.nas.module.file;

import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.file.dto.BatchItemVO;
import com.guicang.nas.module.file.dto.BatchStatusVO;
import com.guicang.nas.module.file.dto.MediaMetadataVO;
import com.guicang.nas.module.file.dto.TranscodeStatusVO;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 批量转码服务：扫描存储根下所有视频，对浏览器不支持的（eac3/ac3/dts/truehd 音轨等）
 * 排队逐个转码为 compat 版本。复用 {@link MediaTranscodeService} 的单任务幂等逻辑。
 * 任务状态内存态，重启后丢失（compat 文件已落盘可复用）。
 */
@Service
public class MediaBatchTranscodeService {

  private static final Logger log = LoggerFactory.getLogger(MediaBatchTranscodeService.class);

  /** 单批最多转码数（避免一次打爆磁盘，超出部分下批再转）。 */
  private static final int MAX_BATCH = 16;

  /** 视频扩展名白名单（与媒体预览/预热一致）。 */
  private static final Set<String> VIDEO_EXTS =
      Set.of("mkv", "mp4", "avi", "mov", "ts", "webm", "flv", "m4v", "rmvb", "wmv");

  private static final String COMPAT_SUFFIX = ".compat.mp4";

  private final StorageService storageService;
  private final MediaInspectService inspectService;
  private final MediaTranscodeService transcodeService;

  /** batchId -> 批次任务（内存态）。 */
  private final Map<String, BatchJob> jobs = new ConcurrentHashMap<>();

  public MediaBatchTranscodeService(
      StorageService storageService,
      MediaInspectService inspectService,
      MediaTranscodeService transcodeService) {
    this.storageService = storageService;
    this.inspectService = inspectService;
    this.transcodeService = transcodeService;
  }

  /** 批次内单项状态（可变）。 */
  static final class BatchItem {
    final String path;
    final String outputPath;
    volatile String status = "PENDING";
    volatile int progress = 0;
    volatile String message = "";

    BatchItem(String path, String outputPath) {
      this.path = path;
      this.outputPath = outputPath;
    }
  }

  static final class BatchJob {
    String id;
    List<BatchItem> items = new ArrayList<>();
    volatile String state = "RUNNING";
    volatile int running = 0;
    volatile int done = 0;
    volatile int failed = 0;
  }

  /**
   * 扫描存储根所有视频，浏览器不支持的入队转码。
   *
   * @return 批次状态；无任务时 batchId 为空、state=DONE
   */
  public BatchStatusVO startAll() {
    // 幂等：已有正在执行的批次则直接返回（避免重复入队）
    for (BatchJob job : jobs.values()) {
      if ("RUNNING".equals(job.state)) {
        log.info("批量转码已有进行中的批次 {}，直接复用", job.id);
        return toVO(job);
      }
    }
    List<String> videos = new ArrayList<>();
    scanDir("", videos);
    List<BatchItem> items = new ArrayList<>();
    for (String rel : videos) {
      if (items.size() >= MAX_BATCH) {
        log.info("批量转码达到单批上限 {}，剩余视频下批再转", MAX_BATCH);
        break;
      }
      if (rel.endsWith(COMPAT_SUFFIX)) {
        continue;
      }
      // 已存在 compat 版本则跳过
      if (Files.exists(storageService.resolveForWrite(MediaTranscodeService.compatName(rel)))) {
        continue;
      }
      MediaMetadataVO vo = inspectService.probe(storageService.resolveFile(rel));
      // 只转「视频流浏览器支持 + 仅音轨不支持」的（如 h264+eac3/ac3）。
      // 视频流本身不支持的（如 h265）转码需整段重编码成本极高，不入批量队列。
      boolean transcodeNeeded =
          Boolean.TRUE.equals(vo.browserVideoSupported())
              && Boolean.FALSE.equals(vo.browserAudioSupported());
      if (transcodeNeeded) {
        items.add(new BatchItem(rel, MediaTranscodeService.compatName(rel)));
      }
    }
    if (items.isEmpty()) {
      return new BatchStatusVO("", "DONE", 0, 0, 0, 0, List.of());
    }
    String id = UUID.randomUUID().toString().substring(0, 8);
    BatchJob job = new BatchJob();
    job.id = id;
    job.items = items;
    jobs.put(id, job);
    Thread worker = new Thread(() -> runJob(job), "batch-" + id);
    worker.setDaemon(true);
    worker.start();
    log.info("批量转码启动: batch={} items={}", id, items.size());
    return toVO(job);
  }

  /** 查询批次状态；批次不存在返回 GONE。 */
  public BatchStatusVO status(String batchId) {
    BatchJob job = jobs.get(batchId);
    if (job == null) {
      return new BatchStatusVO(batchId, "GONE", 0, 0, 0, 0, List.of());
    }
    return toVO(job);
  }

  private void runJob(BatchJob job) {
    for (BatchItem item : job.items) {
      item.status = "RUNNING";
      job.running++;
      TranscodeStatusVO v = transcodeService.start(item.path);
      item.status = v.status();
      item.progress = v.progress();
      item.message = v.message();
      while ("RUNNING".equals(item.status)) {
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          item.status = "FAILED";
          item.message = "批处理中断";
          break;
        }
        TranscodeStatusVO cur = transcodeService.status(item.path);
        item.status = cur.status();
        item.progress = cur.progress();
        item.message = cur.message();
      }
      job.running--;
      if ("DONE".equals(item.status)) {
        job.done++;
      } else {
        job.failed++;
      }
    }
    job.state = "DONE";
    log.info("批量转码完成: batch={} done={} failed={}", job.id, job.done, job.failed);
  }

  /** 递归扫描目录收集视频相对路径（跳过回收站/临时目录）。 */
  private void scanDir(String rel, List<String> out) {
    List<FileEntry> entries;
    try {
      entries = storageService.list(rel);
    } catch (Exception e) {
      log.debug("扫描目录失败: {} -> {}", rel, e.getMessage());
      return;
    }
    for (FileEntry e : entries) {
      String child = e.path();
      if (child.startsWith(".guicang-trash") || child.startsWith(".guicang-tmp")) {
        continue;
      }
      if (e.dir()) {
        scanDir(child, out);
      } else if (isVideo(child)) {
        out.add(child);
      }
    }
  }

  private boolean isVideo(String rel) {
    int dot = rel.lastIndexOf('.');
    if (dot < 0) {
      return false;
    }
    return VIDEO_EXTS.contains(rel.substring(dot + 1).toLowerCase());
  }

  private BatchStatusVO toVO(BatchJob job) {
    List<BatchItemVO> vos =
        job.items.stream()
            .map(i -> new BatchItemVO(i.path, i.status, i.progress, i.message, i.outputPath))
            .toList();
    return new BatchStatusVO(
        job.id, job.state, vos.size(), job.running, job.done, job.failed, vos);
  }
}
