package com.guicang.nas.module.setup;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 系统配置实体，对应 sys_config 表（key-value）。 */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig {

  @TableId(value = "key", type = IdType.INPUT)
  private String key;

  private String value;

  private String updatedAt;
}
