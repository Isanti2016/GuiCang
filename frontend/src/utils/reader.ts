/**
 * 阅读器工具：设置持久化（localStorage）+ 进度换算。
 */

export interface ReaderSettings {
  fontSize: number; // px
  lineHeight: number; // 倍
  theme: "paper" | "dark" | "sepia";
  /** 自动保存进度开关。 */
  autoSave: boolean;
}

const SETTINGS_KEY = "guicang.reader.settings";

export const DEFAULT_SETTINGS: ReaderSettings = {
  fontSize: 18,
  lineHeight: 1.8,
  theme: "paper",
  autoSave: true,
};

export const FONT_SIZE_RANGE = [14, 32] as const;
export const LINE_HEIGHT_RANGE = [1.4, 2.4] as const;

/** 读取阅读设置（含兜底合并）。 */
export function loadSettings(): ReaderSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw) as Partial<ReaderSettings>;
    return { ...DEFAULT_SETTINGS, ...parsed };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

/** 保存阅读设置。 */
export function saveSettings(settings: ReaderSettings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
}

/** 阅读进度自动保存（防抖由调用方负责）。 */
export function clampPercent(p: number): number {
  return Math.max(0, Math.min(100, Math.round(p)));
}

/**
 * 由当前滚动位置估算章节内阅读百分比（0-100）。
 * 若内容不足一屏（无滚动），视为已读 100%。
 */
export function scrollPercent(scrollTop: number, scrollHeight: number, clientHeight: number): number {
  const maxScroll = scrollHeight - clientHeight;
  if (maxScroll <= 0) return 100;
  return clampPercent((scrollTop / maxScroll) * 100);
}

/** 主题 → 阅读区背景/文字色（供样式绑定）。 */
export const THEME_COLORS: Record<
  ReaderSettings["theme"],
  { bg: string; color: string; border: string }
> = {
  paper: { bg: "#f7f3e9", color: "#3f3a32", border: "#e3dcc8" },
  sepia: { bg: "#f2e8d5", color: "#4a3b28", border: "#ddd0b4" },
  dark: { bg: "#1f2430", color: "#c8ccd4", border: "#2c3240" },
};
