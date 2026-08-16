package com.guicang.nas.module.audit;

import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.audit.CurrentUserResolver;
import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面：拦截 {@link Audit} 注解方法，记录操作者/动作/对象/来源与结果。
 *
 * <p>审计写入为尽力而为：写入失败只记日志，不影响业务执行；detail 仅记录异常类型，避免泄露敏感信息。
 */
@Aspect
@Component
public class AuditAspect {

  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  private static final String RESULT_SUCCESS = "success";
  private static final String RESULT_FAILED = "failed";

  private final AuditService auditService;
  private final CurrentUserResolver currentUserResolver;

  public AuditAspect(AuditService auditService, CurrentUserResolver currentUserResolver) {
    this.auditService = auditService;
    this.currentUserResolver = currentUserResolver;
  }

  @Around("@annotation(audit)")
  public Object aroundAuditedMethod(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
    String result = RESULT_SUCCESS;
    String detail = null;
    try {
      return joinPoint.proceed();
    } catch (Throwable e) {
      result = RESULT_FAILED;
      detail = e.getClass().getSimpleName();
      throw e;
    } finally {
      recordAudit(audit, result, detail);
    }
  }

  private void recordAudit(Audit audit, String result, String detail) {
    try {
      auditService.record(
          new AuditLogCreateDTO(
              currentUserResolver.currentUsername().orElse(null),
              audit.action(),
              audit.resource(),
              resolveClientIp(),
              resolveUserAgent(),
              result,
              detail));
    } catch (Exception e) {
      // 审计为尽力而为，失败不应影响业务
      log.error("审计记录写入失败: action={}", audit.action(), e);
    }
  }

  private String resolveClientIp() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return null;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveUserAgent() {
    HttpServletRequest request = currentRequest();
    return request == null ? null : request.getHeader("User-Agent");
  }

  private HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest();
    }
    return null;
  }
}
