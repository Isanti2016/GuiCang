package com.guicang.nas.module.sync;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 同步执行历史实体，对应 sync_history 表。 */
@Getter
@Setter
@TableName("sync_history")
public class SyncHistory {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long taskId;

  /** 任务类型：index_scan / organize。 */
  private String taskType;

  private Long startedAt;

  private Long finishedAt;

  /** running / success / partial / failed。 */
  private String status;

  /** 处理文件总数。 */
  private Integer processed;

  /** 成功数。 */
  private Integer succeeded;

  /** 失败数。 */
  private Integer failed;

  /** 冲突跳过数（skip 策略跳过）。 */
  private Integer skipped;

  /** 索引扫描：新增数。 */
  private Integer added;

  /** 索引扫描：更新数。 */
  private Integer updated;

  /** 索引扫描：删除数。 */
  private Integer deleted;

  private String error;

  /** 执行明细（JSON）。 */
  private String details;
}
