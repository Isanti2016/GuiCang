package com.guicang.nas.module.favorite;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 收藏实体，对应 favorite 表。 */
@Getter
@Setter
@TableName("favorite")
public class Favorite {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String path;

  private String username;

  private Long createdAt;
}
