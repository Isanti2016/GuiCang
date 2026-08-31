package com.guicang.nas.module.reader.dto;

/** 阅读进度响应。 */
public record ReaderProgressVO(
    String path, int chapterIndex, int percent, long updatedAt) {}
