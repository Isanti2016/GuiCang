package com.guicang.nas.common.audit;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 默认解析器：认证链路接入前始终返回空（匿名/系统操作）。
 *
 * <p>注意：Step 2.2 接入 Security 后，用 {@code @Configuration + @Bean + @ConditionalOnMissingBean}
 * 方式替换为本实现（条件注解不能直接用在 @Component 上，会误判自身定义已存在）。
 */
@Component
public class DefaultCurrentUserResolver implements CurrentUserResolver {

  @Override
  public Optional<String> currentUsername() {
    return Optional.empty();
  }
}
