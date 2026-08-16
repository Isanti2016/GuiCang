package com.guicang.nas.module.setup;

import com.guicang.nas.module.setup.dto.SetupInitRequest;
import com.guicang.nas.module.setup.dto.SetupStatusResponse;

/** 首次初始化服务。 */
public interface SetupService {

  /** 当前初始化状态。 */
  SetupStatusResponse status();

  /** 执行初始化（新建管理员元数据 + 触发系统账号供给 + 写完成标记）。 */
  SetupStatusResponse init(SetupInitRequest request);
}
