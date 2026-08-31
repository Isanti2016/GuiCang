package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 媒体探测缓存（独立于 file_index，保证探测结果可复用）。对应 media_metadata_cache 表。 */
@Getter
@Setter
@TableName("media_metadata_cache")
public class MediaMetadataCache {

  /** 存储根下相对路径（主键，不复用自增 id）。 */
  @TableId(type = IdType.INPUT)
  private String path;

  private String container;
  private String videoCodec;
  private String audioCodec;
  private Long durationSec;
  private Integer width;
  private Integer height;
  private String audioCodecs;   // JSON 字符串
  private String videoCodecs;   // JSON 字符串
  private Integer hasSubtitle;
  private Long probedAt;
}
