package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 文件索引实体，对应 file_index 表（文件系统为真源，索引用于搜索/统计）。 */
@Getter
@Setter
@TableName("file_index")
public class FileIndex {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 存储根下相对路径（唯一）。 */
  private String path;

  private String name;

  private String ext;

  /** dir / file / image / video / note / other。 */
  private String kind;

  private Long size;

  /** 修改时间（epoch 毫秒）。 */
  private Long mtime;

  /** 最后操作者。 */
  private String owner;

  private Long indexedAt;

  /** 文本内容（md/txt 全文索引用，其余类型为空）。 */
  private String content;

  /** 首选音轨编码（如 aac/mp3/eac3/opus）；无音轨或未探测时空。 */
  private String audioCodec;

  /** 首选视频编码（如 h264/hevc/vp9/av1）。 */
  private String videoCodec;

  /** 时长（秒）；不可靠时为空。 */
  private Long durationSec;
}
