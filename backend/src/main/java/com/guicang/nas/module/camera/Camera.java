package com.guicang.nas.module.camera;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 监控摄像头注册信息：录像归档时自动注册，名称唯一，可补充位置备注。 */
@Getter
@Setter
@TableName("camera")
public class Camera {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 摄像头名（唯一，通常来自接收目录子目录名）。 */
  private String name;

  /** 位置备注，如「客厅」「门口」。 */
  private String location;

  private Long createdAt;

  private Long updatedAt;
}
