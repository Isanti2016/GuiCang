package com.guicang.nas.module.log;

import com.guicang.nas.module.log.dto.LogPage;
import java.util.List;

/** 系统日志查看服务：读取后端 JSON 日志文件，支持级别/关键字过滤与分页。 */
public interface LogService {

  /**
   * 查询系统日志（按级别/关键字过滤，时间倒序分页）。
   *
   * @param level 日志级别（INFO/WARN/ERROR/DEBUG，可空）
   * @param keyword 关键字（匹配 message/logger，可空）
   * @param page 页码（从 1 起）
   * @param size 每页条数（≤200）
   * @return 日志分页结果
   */
  LogPage query(String level, String keyword, long page, long size);

  /**
   * 支持的日志级别列表。
   *
   * @return 级别名列表（INFO/WARN/ERROR/DEBUG）
   */
  List<String> levels();
}
