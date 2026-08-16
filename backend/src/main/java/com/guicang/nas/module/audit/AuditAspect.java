package com.guicang.nas.module.audit;

import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.audit.CurrentUserResolver;
import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计切面：拦截 {@link Audit} 注解方法，记录操作者/动作/对象/来源与结果。
 *
 * <p>resource 支持 SpEL 表达式（以 # 开头，按方法参数名求值，可用 #result 引用返回值）， 编译需开启 {@code -parameters}。
 *
 * <p>审计写入为尽力而为：写入失败只记日志，不影响业务执行；detail 仅记录异常类型，避免泄露敏感信息。
 */
@Aspect
@Component
public class AuditAspect {

  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  private static final String RESULT_SUCCESS = "success";
  private static final String RESULT_FAILED = "failed";
  private static final String SPEL_PREFIX = "#";

  private final AuditService auditService;
  private final CurrentUserResolver currentUserResolver;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final ParameterNameDiscoverer parameterNameDiscoverer =
      new DefaultParameterNameDiscoverer();

  public AuditAspect(AuditService auditService, CurrentUserResolver currentUserResolver) {
    this.auditService = auditService;
    this.currentUserResolver = currentUserResolver;
  }

  @Around("@annotation(audit)")
  public Object aroundAuditedMethod(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
    String result = RESULT_SUCCESS;
    String detail = null;
    Object returnValue = null;
    try {
      returnValue = joinPoint.proceed();
      return returnValue;
    } catch (Throwable e) {
      result = RESULT_FAILED;
      detail = e.getClass().getSimpleName();
      throw e;
    } finally {
      recordAudit(audit, resolveResource(audit.resource(), joinPoint, returnValue), result, detail);
    }
  }

  private String resolveResource(
      String resource, ProceedingJoinPoint joinPoint, Object returnValue) {
    if (resource == null || !resource.startsWith(SPEL_PREFIX)) {
      return resource;
    }
    try {
      EvaluationContext context = buildEvaluationContext(joinPoint, returnValue);
      return String.valueOf(expressionParser.parseExpression(resource).getValue(context));
    } catch (Exception e) {
      log.warn("审计 resource SpEL 解析失败: {}", resource, e);
      return resource;
    }
  }

  private EvaluationContext buildEvaluationContext(
      ProceedingJoinPoint joinPoint, Object returnValue) {
    StandardEvaluationContext context = new StandardEvaluationContext();
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
    Object[] args = joinPoint.getArgs();
    if (parameterNames != null) {
      for (int i = 0; i < parameterNames.length && i < args.length; i++) {
        context.setVariable(parameterNames[i], args[i]);
      }
    }
    context.setVariable("result", returnValue);
    return context;
  }

  private void recordAudit(Audit audit, String resource, String result, String detail) {
    try {
      auditService.record(
          new AuditLogCreateDTO(
              currentUserResolver.currentUsername().orElse(null),
              audit.action(),
              resource,
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
