package com.guicang.nas.module.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 审计日志实体，对应 audit_log 表。 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 操作者用户名（系统操作可为空）。 */
  private String username;

  /** 审计动作，如 login / file.upload / user.create。 */
  private String action;

  /** 审计对象（路径/用户名等）。 */
  private String resource;

  /** 来源 IP。 */
  private String ip;

  /** User-Agent。 */
  private String userAgent;

  /** 结果：success / failed。 */
  private String result;

  /** 补充说明（失败原因等）。 */
  private String detail;

  /** 创建时间（epoch 毫秒）。 */
  private Long createdAt;
}
