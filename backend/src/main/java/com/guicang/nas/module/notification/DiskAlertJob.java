package com.guicang.nas.module.notification;

import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.setting.SysSetting;
import com.guicang.nas.module.setting.SysSettingService;
import java.nio.file.FileStore;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 磁盘空间告警定时任务：每小时检查存储根使用率，超阈值生成站内通知（24h 去重）。 */
@Component
public class DiskAlertJob {

  private static final Logger log = LoggerFactory.getLogger(DiskAlertJob.class);

  private final StorageService storageService;
  private final NotificationService notificationService;
  private final SysSettingService sysSettingService;

  public DiskAlertJob(
      StorageService storageService,
      NotificationService notificationService,
      SysSettingService sysSettingService) {
    this.storageService = storageService;
    this.notificationService = notificationService;
    this.sysSettingService = sysSettingService;
  }

  /** 每小时执行一次。 */
  @Scheduled(cron = "0 0 * * * ?")
  public void checkDisk() {
    int threshold = sysSettingService.getInt(SysSetting.DISK_ALERT_THRESHOLD);
    if (threshold <= 0) {
      return;
    }
    try {
      FileStore store = Files.getFileStore(storageService.root());
      long total = store.getTotalSpace();
      long usable = store.getUsableSpace();
      if (total <= 0) {
        return;
      }
      int usage = (int) Math.round((1.0 - (double) usable / total) * 100);
      if (usage >= threshold && !notificationService.hasRecent("disk", 24L * 3600 * 1000)) {
        notificationService.create(
            "disk", "磁盘空间告警",
            "存储空间已使用 " + usage + "%（阈值 " + threshold + "%），请及时清理。", null);
        log.info("磁盘空间告警已生成：使用 {}%", usage);
      }
    } catch (Exception e) {
      log.warn("磁盘告警检查失败: {}", e.getMessage());
    }
  }
}
