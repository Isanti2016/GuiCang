package com.guicang.nas.module.sync;

import java.nio.file.Path;

/** 自动整理规则：根据文件计算目标子目录（相对任务 targetConfig）。 */
public interface OrganizeRule {

  /**
   * 计算文件应归档到的目标子目录（相对 targetConfig，不含文件名）。
   *
   * @param file 源文件绝对路径
   * @param name 文件名
   * @return 相对子目录，如 2026/08 或 image；空串表示直接放入目标目录
   */
  String subPath(Path file, String name);
}
