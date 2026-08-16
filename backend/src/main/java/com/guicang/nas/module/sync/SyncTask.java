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

  /** directory（一期）。 */
  private String sourceType;

  /** 源目录相对路径。 */
  private String sourceConfig;

  /** Quartz cron。 */
  private String cron;

  private Integer enabled;

  private Long lastRunAt;

  private String lastStatus;

  private String createdAt;
}
