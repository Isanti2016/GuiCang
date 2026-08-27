package com.guicang.nas.module.audit;

import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.audit.CurrentUserResolver;
import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
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
      recordAudit(
          joinPoint,
          audit,
          resolveResource(audit.resource(), joinPoint, returnValue),
          result,
          detail);
    }
  }

  private String resolveResource(
      String resource, ProceedingJoinPoint joinPoint, Object returnValue) {
    if (resource == null || !resource.startsWith(SPEL_PREFIX)) {
      return resource;
    }
    try {
      EvaluationContext context = buildEvaluationContext(joinPoint, returnValue);
      Object value = expressionParser.parseExpression(resource).getValue(context);
      return formatResource(value);
    } catch (Exception e) {
      log.warn("审计 resource SpEL 解析失败: {}", resource, e);
      return resource;
    }
  }

  /** 格式化审计对象：集合转可读文本（如批量路径 "a.jpg、b.jpg 等 5 项"），其他直接字符串化。 */
  private String formatResource(Object value) {
    if (value instanceof java.util.Collection<?> items) {
      if (items.isEmpty()) {
        return "";
      }
      List<?> first = items.stream().limit(3).toList();
      String joined =
          first.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("、"));
      return items.size() > 3 ? joined + " 等 " + items.size() + " 项" : joined;
    }
    return value == null ? "" : String.valueOf(value);
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

  private void recordAudit(
      ProceedingJoinPoint joinPoint, Audit audit, String resource, String result, String detail) {
    try {
      auditService.record(
          new AuditLogCreateDTO(
              resolveUsername(audit, joinPoint),
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

  /**
   * 解析操作者用户名：优先取当前登录用户；未登录（如登录动作本身）时从方法参数中查找 带 username() 访问器的对象（如 LoginRequest）兜底。
   *
   * @param audit 审计注解（暂留，便于后续扩展按注解指定取参路径）
   * @param joinPoint 被拦截方法的连接点
   * @return 操作者用户名；解析不到返回 null
   */
  private String resolveUsername(Audit audit, ProceedingJoinPoint joinPoint) {
    return currentUserResolver
        .currentUsername()
        .orElseGet(() -> extractUsernameFromArgs(joinPoint.getArgs()));
  }

  private String extractUsernameFromArgs(Object[] args) {
    if (args == null) {
      return null;
    }
    for (Object arg : args) {
      if (arg == null) {
        continue;
      }
      try {
        Method usernameMethod = arg.getClass().getMethod("username");
        if (usernameMethod.getReturnType() == String.class) {
          String username = (String) usernameMethod.invoke(arg);
          if (username != null && !username.isBlank()) {
            return username;
          }
        }
      } catch (ReflectiveOperationException | SecurityException e) {
        // 参数无 username() 访问器，跳过继续查找
      }
    }
    return null;
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
