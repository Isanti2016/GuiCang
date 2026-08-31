package com.guicang.nas.module.reader;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 阅读进度：按用户 + 文件唯一，记住读到第几章与章节内百分比。 */
@Getter
@Setter
@TableName("reading_progress")
public class ReadingProgress {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 读者用户名。 */
  private String username;

  /** 书籍文件相对路径。 */
  private String filePath;

  /** 当前章节序号（0 起）。 */
  private Integer chapterIndex;

  /** 章节内阅读百分比 0-100。 */
  private Integer percent;

  /** 更新时间（毫秒）。 */
  private Long updatedAt;
}
