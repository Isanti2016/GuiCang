import { beforeEach, describe, expect, it } from "vitest";
import {
  clampPercent,
  DEFAULT_SETTINGS,
  loadSettings,
  saveSettings,
  scrollPercent,
  THEME_COLORS,
} from "@/utils/reader";

describe("阅读设置持久化", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("无存档时返回默认设置", () => {
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
  });

  it("损坏的 JSON 回退默认设置", () => {
    localStorage.setItem("guicang.reader.settings", "{oops");
    expect(loadSettings()).toEqual(DEFAULT_SETTINGS);
  });

  it("保存后能读回", () => {
    saveSettings({ ...DEFAULT_SETTINGS, fontSize: 24, theme: "dark" });
    const loaded = loadSettings();
    expect(loaded.fontSize).toBe(24);
    expect(loaded.theme).toBe("dark");
    // 未提供的字段兜底为默认值
    expect(loaded.autoSave).toBe(true);
  });

  it("部分存档合并默认值", () => {
    localStorage.setItem("guicang.reader.settings", JSON.stringify({ fontSize: 30 }));
    const loaded = loadSettings();
    expect(loaded.fontSize).toBe(30);
    expect(loaded.lineHeight).toBe(DEFAULT_SETTINGS.lineHeight);
  });
});

describe("进度百分比换算", () => {
  it("clampPercent 夹取到 0-100", () => {
    expect(clampPercent(-5)).toBe(0);
    expect(clampPercent(150)).toBe(100);
    expect(clampPercent(42.4)).toBe(42);
    expect(clampPercent(0)).toBe(0);
    expect(clampPercent(100)).toBe(100);
  });

  it("内容不足一屏视为已读 100%", () => {
    expect(scrollPercent(0, 500, 600)).toBe(100);
    expect(scrollPercent(0, 600, 600)).toBe(100);
  });

  it("顶部为 0%，底部为 100%，中间按比例", () => {
    expect(scrollPercent(0, 1600, 600)).toBe(0);
    expect(scrollPercent(1000, 1600, 600)).toBe(100);
    expect(scrollPercent(500, 1600, 600)).toBe(50);
  });
});

describe("主题色", () => {
  it("覆盖全部主题且各色板字段齐全", () => {
    for (const theme of ["paper", "sepia", "dark"] as const) {
      const palette = THEME_COLORS[theme];
      expect(typeof palette.bg).toBe("string");
      expect(typeof palette.color).toBe("string");
      expect(typeof palette.border).toBe("string");
    }
  });
});
