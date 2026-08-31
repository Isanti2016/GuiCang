package com.guicang.nas.module.reader;

/** 小说格式枚举：当前支持 TXT（编码探测 + 章节正则）与 EPUB（zip + OPF spine）。 */
public enum NovelFormat {
  TXT,
  EPUB
}
