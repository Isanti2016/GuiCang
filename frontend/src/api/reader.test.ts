import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
const putMock = vi.fn();

vi.mock("@/utils/http", () => ({
  get: (...args: unknown[]) => getMock(...args),
  put: (...args: unknown[]) => putMock(...args),
}));

import {
  fetchChapter,
  fetchProgress,
  openNovel,
  saveProgress,
  type ChapterContent,
  type NovelMeta,
} from "@/api/reader";

describe("reader API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("openNovel 携带 path 查询参数", async () => {
    const meta: NovelMeta = {
      path: "books/仙路风云.txt",
      title: "仙路风云",
      author: null,
      format: "TXT",
      encoding: "UTF-8",
      fileSize: 1024,
      chapterCount: 3,
      chapters: [
        { index: 0, title: "第一章 重生归来" },
        { index: 1, title: "第二章 初次修炼" },
      ],
    };
    getMock.mockResolvedValue(meta);

    const result = await openNovel("books/仙路风云.txt");

    expect(getMock).toHaveBeenCalledWith("/reader/novel", { path: "books/仙路风云.txt" });
    expect(result).toEqual(meta);
  });

  it("fetchChapter 携带 path 与 index", async () => {
    const content: ChapterContent = {
      path: "books/仙路风云.txt",
      index: 1,
      total: 3,
      title: "第二章 初次修炼",
      content: "正文二。",
    };
    getMock.mockResolvedValue(content);

    const result = await fetchChapter("books/仙路风云.txt", 1);

    expect(getMock).toHaveBeenCalledWith("/reader/chapter", {
      path: "books/仙路风云.txt",
      index: 1,
    });
    expect(result.index).toBe(1);
    expect(result.content).toBe("正文二。");
  });

  it("fetchProgress 未读返回 null", async () => {
    getMock.mockResolvedValue(null);
    const result = await fetchProgress("books/剑来.epub");
    expect(getMock).toHaveBeenCalledWith("/reader/progress", { path: "books/剑来.epub" });
    expect(result).toBeNull();
  });

  it("saveProgress 提交章节与百分比", async () => {
    putMock.mockResolvedValue(undefined);
    await saveProgress("books/仙路风云.txt", 2, 65);
    expect(putMock).toHaveBeenCalledWith("/reader/progress", {
      path: "books/仙路风云.txt",
      chapterIndex: 2,
      percent: 65,
    });
  });
});
