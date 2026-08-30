package com.guicang.nas.module.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 站内通知实体，对应 notification 表。 */
@Getter
@Setter
@TableName("notification")
public class Notification {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** disk / sync / system。 */
  private String type;

  private String title;

  private String content;

  /** 接收者（null = 广播给所有用户）。 */
  private String username;

  /** 0 未读 / 1 已读。 */
  private Integer readFlag;

  private Long createdAt;
}
