package com.guicang.nas.module.audit;

import com.guicang.nas.common.Result;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审计查询接口（需 audit.view 权限，默认仅管理员）。 */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAuthority('audit.view')")
public class AuditController {

  private final AuditService auditService;

  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  /** 审计记录列表（筛选 + 分页）。 */
  @GetMapping("/logs")
  public Result<AuditPage> logs(
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String result,
      @RequestParam(defaultValue = "1") long page,
      @RequestParam(defaultValue = "20") long size) {
    return Result.ok(
        new AuditPage(
            auditService.query(username, action, result, page, size),
            auditService.count(username, action, result)));
  }

  /** 审计分页结果。 */
  public record AuditPage(List<AuditLog> records, long total) {}
}
