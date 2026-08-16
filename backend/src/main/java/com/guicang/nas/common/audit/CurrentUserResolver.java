package com.guicang.nas.common.audit;

import java.util.Optional;

/** 当前登录用户名解析器；认证链路（Step 2.2）接入后由 Security 实现提供。 */
public interface CurrentUserResolver {

  /** 返回当前登录用户名；未登录时为空。 */
  Optional<String> currentUsername();
}
