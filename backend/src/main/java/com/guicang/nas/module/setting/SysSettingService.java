package com.guicang.nas.module.setting;

import java.util.List;
import java.util.Map;

/** 系统设置服务：基于 sys_config 的通用 key-value 读写，支持按定义注册扩展。 */
public interface SysSettingService {

  /**
   * 获取全部设置项（含默认值，未显式设置的返回默认值）。
   *
   * @return key → 当前值（字符串形式）
   */
  Map<String, String> all();

  /**
   * 更新一个或多个设置项（仅接受已注册的 key；type 校验）。
   *
   * @param values 待更新的 key → value
   */
  void update(Map<String, String> values);

  /**
   * 读取单个设置项的整数值。
   *
   * @param setting 设置项定义
   * @return 整数值；解析失败返回默认值
   */
  int getInt(SysSetting setting);

  /**
   * 设置项定义列表（前端可动态渲染表单）。
   *
   * @return 全部已注册设置项
   */
  List<SysSetting> definitions();
}
