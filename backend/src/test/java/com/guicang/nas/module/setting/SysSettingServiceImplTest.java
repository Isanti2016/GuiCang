package com.guicang.nas.module.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.module.setup.SysConfig;
import com.guicang.nas.module.setup.SysConfigMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 系统设置服务测试：默认值、读写、校验。 */
@SpringBootTest
@ActiveProfiles("test")
class SysSettingServiceImplTest {

  @Autowired private SysSettingService sysSettingService;
  @Autowired private SysConfigMapper sysConfigMapper;

  @BeforeEach
  void clean() {
    // 清理设置键，保证用例独立（共享测试库）
    sysConfigMapper.delete(
        new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getKey, "trash.auto-purge-days"));
  }

  @Test
  void 未设置时返回默认值() {
    Map<String, String> all = sysSettingService.all();
    assertThat(all).containsEntry("trash.auto-purge-days", "0");
  }

  @Test
  void 更新后读取新值() {
    sysSettingService.update(Map.of("trash.auto-purge-days", "30"));
    assertThat(sysSettingService.all()).containsEntry("trash.auto-purge-days", "30");
    SysConfig stored = sysConfigMapper.selectById("trash.auto-purge-days");
    assertThat(stored).isNotNull();
    assertThat(stored.getValue()).isEqualTo("30");
  }

  @Test
  void 非法整数被拒绝() {
    assertThatThrownBy(() -> sysSettingService.update(Map.of("trash.auto-purge-days", "abc")))
        .isInstanceOf(BizException.class);
  }

  @Test
  void 未知设置项被拒绝() {
    assertThatThrownBy(() -> sysSettingService.update(Map.of("unknown.key", "1")))
        .isInstanceOf(BizException.class);
  }

  @Test
  void getInt解析() {
    sysSettingService.update(Map.of("trash.auto-purge-days", "7"));
    assertThat(sysSettingService.getInt(SysSetting.TRASH_AUTO_PURGE_DAYS)).isEqualTo(7);
  }
}
