package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 文件历史版本实体，对应 file_version 表。 */
@Getter
@Setter
@TableName("file_version")
public class FileVersion {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String path;

  private String content;

  private Long size;

  private String createdBy;

  private Long createdAt;
}
