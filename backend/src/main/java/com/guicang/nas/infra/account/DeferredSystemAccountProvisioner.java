package com.guicang.nas.infra.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 延迟供给实现（helper 未接入前占位）。
 *
 * <p>Step 2.1 编写 guicang-helper（user-add 等）后，以 {@code @Configuration + @Bean
 * + @ConditionalOnMissingBean} 方式替换为本实现，避免条件注解直接用在 @Component 上的误判。
 */
@Component
public class DeferredSystemAccountProvisioner implements SystemAccountProvisioner {

  private static final Logger log = LoggerFactory.getLogger(DeferredSystemAccountProvisioner.class);

  @Override
  public ProvisionResult provisionAdmin(String username) {
    log.warn("系统账号供给未就绪（guicang-helper 待 Step 2.1 接入），管理员账号 {} 待创建", username);
    return new ProvisionResult(ProvisionStatus.PENDING, "guicang-helper 未接入，系统账号待 Step 2.1 创建");
  }
}
