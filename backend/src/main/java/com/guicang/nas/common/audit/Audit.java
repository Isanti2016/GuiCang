package com.guicang.nas.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解：标注在需要留痕的业务方法上，由 {@code AuditAspect} 拦截并写入 audit_log。
 *
 * <p>用法示例：{@code @Audit(action = "user.create", resource = "alice")}
 *
 * <p>说明：resource 当前为静态字符串；后续如需要动态取参（如登录用户名），可扩展 SpEL 求值。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

  /** 审计动作，如 login / file.upload / user.create。 */
  String action();

  /** 审计对象（路径/用户名等），默认空串。 */
  String resource() default "";
}
