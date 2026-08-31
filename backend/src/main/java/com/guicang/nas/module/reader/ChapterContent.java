package com.guicang.nas.module.reader;

/** 章节正文响应：标题 + 正文 + 章节位置（供阅读器展示与翻页）。 */
public record ChapterContent(
    String path,
    int index,
    int total,
    String title,
    String content) {}
