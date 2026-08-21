package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 回收站条目实体，对应 trash_item 表。 */
@Getter
@Setter
@TableName("trash_item")
public class TrashItem {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 原路径（恢复目标）。 */
  private String originalPath;

  /** 回收站内路径。 */
  private String trashPath;

  /** 删除者。 */
  private String username;

  private String kind;

  private Long size;

  private Long deletedAt;
}
