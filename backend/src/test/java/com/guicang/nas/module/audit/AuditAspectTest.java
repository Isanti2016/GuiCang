package com.guicang.nas.module.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 审计切面行为测试：注解方法执行后 audit_log 有记录。 */
@SpringBootTest
@ActiveProfiles("test")
class AuditAspectTest {

  @Autowired private TestAuditedService testAuditedService;

  @Autowired private AuditLogMapper auditLogMapper;

  @Test
  void 注解方法成功后写入审计() {
    testAuditedService.doWork("abc");

    AuditLog record = latestRecord("test.operation");
    assertThat(record).isNotNull();
    assertThat(record.getResult()).isEqualTo("success");
    assertThat(record.getResource()).isEqualTo("test-resource");
    assertThat(record.getUsername()).isNull();
  }

  @Test
  void 失败方法写入failed且异常继续抛出() {
    assertThatThrownBy(() -> testAuditedService.fail()).isInstanceOf(IllegalStateException.class);

    AuditLog record = latestRecord("test.failure");
    assertThat(record).isNotNull();
    assertThat(record.getResult()).isEqualTo("failed");
    assertThat(record.getDetail()).isEqualTo("IllegalStateException");
  }

  private AuditLog latestRecord(String action) {
    return auditLogMapper.selectOne(
        new LambdaQueryWrapper<AuditLog>().eq(AuditLog::getAction, action).last("LIMIT 1"));
  }
}
