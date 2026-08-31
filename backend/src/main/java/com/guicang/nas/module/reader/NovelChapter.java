package com.guicang.nas.module.reader;

/** 小说章节元数据：序号 + 标题（正文按需读取，不在此携带）。 */
public record NovelChapter(int index, String title) {}
