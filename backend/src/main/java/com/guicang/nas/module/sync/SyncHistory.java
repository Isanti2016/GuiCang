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

  private Long startedAt;

  private Long finishedAt;

  /** running / success / failed。 */
  private String status;

  private Integer added;

  private Integer updated;

  private Integer deleted;

  private String error;
}
