package com.guicang.nas.module.reader;

import java.util.List;

/** 小说整体信息：书名/作者/格式/编码/章节列表（用于打开书籍与目录展示）。 */
public record NovelMeta(
    String path,
    String title,
    String author,
    NovelFormat format,
    String encoding,
    long fileSize,
    int chapterCount,
    List<NovelChapter> chapters) {}
