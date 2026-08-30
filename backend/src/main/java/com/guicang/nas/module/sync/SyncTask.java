package com.guicang.nas.module.sync;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 同步任务实体，对应 sync_task 表。 */
@Getter
@Setter
@TableName("sync_task")
public class SyncTask {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  /** directory（一期遗留，保留兼容）。 */
  private String sourceType;

  /** 任务类型：index_scan（索引扫描） / organize（自动整理）。 */
  private String taskType;

  /** 源目录相对路径。 */
  private String sourceConfig;

  /** 目标目录相对路径（自动整理用）。 */
  private String targetConfig;

  /** 整理规则：date_year / date_month / date_day / kind / device。 */
  private String ruleType;

  /** 规则附加配置（JSON）。 */
  private String ruleConfig;

  /** 操作方式：move / copy。 */
  private String action;

  /** 冲突处理：skip / overwrite / rename。 */
  private String conflict;

  /** Quartz cron。 */
  private String cron;

  private Integer enabled;

  private Long lastRunAt;

  /** running / success / partial / failed。 */
  private String lastStatus;

  private String createdAt;
}
