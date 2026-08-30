package com.guicang.nas.module.setting;

import com.guicang.nas.common.BizException;
import com.guicang.nas.module.setup.SysConfig;
import com.guicang.nas.module.setup.SysConfigMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统设置服务实现：读写 sys_config，未设置项返回注册的默认值。 */
@Service
public class SysSettingServiceImpl implements SysSettingService {

  private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private final SysConfigMapper sysConfigMapper;

  /** 已注册设置项（新增设置在此扩展）。 */
  private static final List<SysSetting> DEFINITIONS =
      List.of(
          SysSetting.TRASH_AUTO_PURGE_DAYS,
          SysSetting.DISK_ALERT_THRESHOLD,
          SysSetting.CAMERA_RECEIVE_DIR);

  public SysSettingServiceImpl(SysConfigMapper sysConfigMapper) {
    this.sysConfigMapper = sysConfigMapper;
  }

  @Override
  public Map<String, String> all() {
    Map<String, String> stored =
        sysConfigMapper.selectList(null).stream()
            .collect(
                Collectors.toMap(
                    SysConfig::getKey, c -> c.getValue() == null ? "" : c.getValue(), (a, b) -> b));
    return DEFINITIONS.stream()
        .collect(
            Collectors.toMap(SysSetting::key, s -> stored.getOrDefault(s.key(), s.defaultValue())));
  }

  @Override
  @Transactional
  public void update(Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      throw new BizException("没有可更新的设置");
    }
    Map<String, SysSetting> defs =
        DEFINITIONS.stream().collect(Collectors.toMap(SysSetting::key, Function.identity()));
    for (Map.Entry<String, String> entry : values.entrySet()) {
      SysSetting def = defs.get(entry.getKey());
      if (def == null) {
        throw new BizException("未知设置项: " + entry.getKey());
      }
      String value = entry.getValue() == null ? "" : entry.getValue().trim();
      validate(def, value);
      SysConfig config = sysConfigMapper.selectById(def.key());
      if (config == null) {
        config = new SysConfig();
        config.setKey(def.key());
        config.setValue(value);
        config.setUpdatedAt(LocalDateTime.now().format(FMT));
        sysConfigMapper.insert(config);
      } else {
        config.setValue(value);
        config.setUpdatedAt(LocalDateTime.now().format(FMT));
        sysConfigMapper.updateById(config);
      }
    }
  }

  @Override
  public int getInt(SysSetting setting) {
    try {
      return Integer.parseInt(all().getOrDefault(setting.key(), setting.defaultValue()));
    } catch (NumberFormatException e) {
      return Integer.parseInt(setting.defaultValue());
    }
  }

  @Override
  public List<SysSetting> definitions() {
    return new ArrayList<>(DEFINITIONS);
  }

  private void validate(SysSetting def, String value) {
    switch (def.type()) {
      case "int" -> {
        try {
          Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new BizException("设置项「" + def.label() + "」必须为整数");
        }
      }
      case "bool" -> {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
          throw new BizException("设置项「" + def.label() + "」必须为 true/false");
        }
      }
      default -> {
        /* string 无额外校验 */
      }
    }
  }
}
