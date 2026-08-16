package com.guicang.nas.infra.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 延迟供给实现（guicang-helper 未部署前的占位，全部返回 PENDING）。
 *
 * <p>Step 2.1 部署 helper 后，以 {@code @Configuration + @Bean + @ConditionalOnMissingBean}
 * 方式替换为本实现（条件注解不能直接用在 @Component 上）。
 */
@Component
public class DeferredSystemAccountProvisioner implements SystemAccountProvisioner {

  private static final Logger log = LoggerFactory.getLogger(DeferredSystemAccountProvisioner.class);
  private static final String PENDING_MESSAGE = "guicang-helper 未部署，系统账号操作待执行";

  @Override
  public ProvisionResult provisionAdmin(String username) {
    log.warn("系统账号供给未就绪（guicang-helper 待部署），管理员账号 {} 待创建", username);
    return ProvisionResult.pending(PENDING_MESSAGE);
  }

  @Override
  public ProvisionResult createUser(String username, String password) {
    log.warn("系统账号供给未就绪（guicang-helper 待部署），用户 {} 创建被延迟", username);
    return ProvisionResult.pending(PENDING_MESSAGE);
  }

  @Override
  public ProvisionResult deleteUser(String username, boolean removeHome) {
    log.warn("系统账号供给未就绪（guicang-helper 待部署），用户 {} 删除被延迟", username);
    return ProvisionResult.pending(PENDING_MESSAGE);
  }

  @Override
  public ProvisionResult setUserStatus(String username, boolean enabled) {
    log.warn("系统账号供给未就绪（guicang-helper 待部署），用户 {} 状态变更被延迟", username);
    return ProvisionResult.pending(PENDING_MESSAGE);
  }

  @Override
  public ProvisionResult setUserPassword(String username, String password) {
    log.warn("系统账号供给未就绪（guicang-helper 待部署），用户 {} 改密被延迟", username);
    return ProvisionResult.pending(PENDING_MESSAGE);
  }
}
