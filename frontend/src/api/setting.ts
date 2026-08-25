import { get, put } from "@/utils/http";

/** 系统设置项定义（与后端 SysSetting 对应）。 */
export interface SysSetting {
  key: string;
  defaultValue: string;
  label: string;
  type: string;
}

/** 获取全部设置项（含默认值）。 */
export function fetchSettings(): Promise<Record<string, string>> {
  return get<Record<string, string>>("/settings");
}

/** 更新设置项。 */
export function updateSettings(values: Record<string, string>): Promise<void> {
  return put<void>("/settings", { values });
}

/** 设置项定义列表（前端动态渲染表单）。 */
export function fetchSettingDefinitions(): Promise<SysSetting[]> {
  return get<SysSetting[]>("/settings/definitions");
}
