package com.guicang.nas.module.audit;

import com.guicang.nas.common.audit.Audit;
import org.springframework.stereotype.Service;

/** 供审计切面测试使用的测试服务（仅存在于 test 源集）。 */
@Service
public class TestAuditedService {

  @Audit(action = "test.operation", resource = "test-resource")
  public String doWork(String input) {
    return "ok:" + input;
  }

  @Audit(action = "test.failure")
  public void fail() {
    throw new IllegalStateException("boom");
  }
}
