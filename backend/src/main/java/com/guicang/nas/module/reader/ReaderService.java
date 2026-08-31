package com.guicang.nas.module.reader;

import com.guicang.nas.module.reader.dto.ReaderProgressVO;
import com.guicang.nas.module.reader.dto.SaveProgressRequest;

/** 小说阅读服务：格式识别、章节读取、进度记忆。 */
public interface ReaderService {

  /** 打开书籍：识别格式并返回元数据 + 章节列表（不含正文）。 */
  NovelMeta open(String path);

  /** 读取指定章节正文。 */
  ChapterContent chapter(String path, int index);

  /** 查询当前用户对该书的阅读进度（无记录返回 null）。 */
  ReaderProgressVO progress(String path);

  /** 保存阅读进度。 */
  void saveProgress(SaveProgressRequest request);
}
