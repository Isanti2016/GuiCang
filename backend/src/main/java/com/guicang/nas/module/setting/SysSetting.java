package com.guicang.nas.module.setting;

/**
 * 系统设置项定义：key + 默认值 + 说明。新增设置项只需在此注册，并加入
 * {@code SysSettingServiceImpl.DEFINITIONS}。
 *
 * @param key 设置键（存 sys_config.key）
 * @param defaultValue 默认值（读取时无记录则返回）
 * @param label 中文说明（前端展示）
 * @param type 值类型：int / bool / string
 */
public record SysSetting(String key, String defaultValue, String label, String type) {

  /** 回收站自动清空天数（0=不自动清空）。 */
  public static final SysSetting TRASH_AUTO_PURGE_DAYS =
      new SysSetting("trash.auto-purge-days", "0", "回收站自动清空天数", "int");

  /** 磁盘空间告警阈值（百分比，0=不告警）。 */
  public static final SysSetting DISK_ALERT_THRESHOLD =
      new SysSetting("disk.alert-threshold", "90", "磁盘空间告警阈值(%)", "int");

  /** 监控录像接收目录（留空=存储根/cameras/incoming）。 */
  public static final SysSetting CAMERA_RECEIVE_DIR =
      new SysSetting("camera.receive-dir", "", "监控录像接收目录", "string");
}
