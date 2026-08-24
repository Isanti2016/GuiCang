package com.guicang.nas.module.log;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.log.dto.LogPage;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统日志查询接口（需 audit.view 权限，默认仅管理员）。 */
@RestController
@RequestMapping("/api/v1/logs")
@PreAuthorize("hasAuthority('audit.view')")
public class LogController {

  private final LogService logService;

  public LogController(LogService logService) {
    this.logService = logService;
  }

  /**
   * 系统日志列表（级别/关键字过滤 + 分页，时间倒序）。
   *
   * @param level 日志级别（DEBUG/INFO/WARN/ERROR，可空=全部）
   * @param keyword 关键字（匹配消息/记录器，可空）
   * @param page 页码（从 1 起）
   * @param size 每页条数（≤200）
   * @return 日志分页结果
   */
  @GetMapping
  public Result<LogPage> logs(
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "50") long size) {
    return Result.ok(logService.query(level, keyword, page, size));
  }

  /**
   * 支持的日志级别。
   *
   * @return 级别列表
   */
  @GetMapping("/levels")
  public Result<List<String>> levels() {
    return Result.ok(logService.levels());
  }
}
