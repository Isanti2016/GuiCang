package com.guicang.nas.module.file;

import com.guicang.nas.module.setting.SysSetting;
import com.guicang.nas.module.setting.SysSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回收站自动清空定时任务：每天 02:00 执行，按系统设置「回收站自动清空天数」清理过期条目。
 *
 * <p>设置项 trash.auto-purge-days=0 时不执行（默认关闭）。
 */
@Component
public class TrashPurgeJob {

  private static final Logger log = LoggerFactory.getLogger(TrashPurgeJob.class);

  private final FileService fileService;
  private final SysSettingService sysSettingService;

  public TrashPurgeJob(FileService fileService, SysSettingService sysSettingService) {
    this.fileService = fileService;
    this.sysSettingService = sysSettingService;
  }

  /** 每天 02:00 清理过期回收站条目。 */
  @Scheduled(cron = "0 0 2 * * ?")
  public void purgeExpired() {
    int days = sysSettingService.getInt(SysSetting.TRASH_AUTO_PURGE_DAYS);
    if (days <= 0) {
      log.debug("回收站自动清空未开启（trash.auto-purge-days=0），跳过");
      return;
    }
    try {
      int count = fileService.purgeExpiredTrash(days);
      log.info("回收站自动清空完成，清理 {} 条", count);
    } catch (Exception e) {
      log.error("回收站自动清空失败: {}", e.getMessage());
    }
  }
}
