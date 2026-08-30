package com.guicang.nas.module.share;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 分享链接实体，对应 share 表。 */
@Getter
@Setter
@TableName("share")
public class Share {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 分享凭证（随机 token）。 */
  private String token;

  /** 分享的文件/目录相对路径。 */
  private String path;

  /** 访问密码（SHA-256 哈希，可空）。 */
  private String password;

  /** 过期时间戳（毫秒，可空表示永不过期）。 */
  private Long expiresAt;

  private String createdBy;

  private Long createdAt;
}
