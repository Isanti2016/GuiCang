import { get, put } from "@/utils/http";

/** 章节元数据。 */
export interface NovelChapter {
  index: number;
  title: string;
}

/** 小说元数据（打开书籍返回；TXT 无作者信息时为 null）。 */
export interface NovelMeta {
  path: string;
  title: string;
  author: string | null;
  format: "TXT" | "EPUB";
  encoding: string;
  fileSize: number;
  chapterCount: number;
  chapters: NovelChapter[];
}

/** 章节正文。 */
export interface ChapterContent {
  path: string;
  index: number;
  total: number;
  title: string;
  content: string;
}

/** 阅读进度。 */
export interface ReaderProgress {
  path: string;
  chapterIndex: number;
  percent: number;
  updatedAt: number;
}

/** 打开书籍：元数据 + 章节列表。 */
export function openNovel(path: string): Promise<NovelMeta> {
  return get<NovelMeta>("/reader/novel", { path });
}

/** 读取章节正文。 */
export function fetchChapter(path: string, index: number): Promise<ChapterContent> {
  return get<ChapterContent>("/reader/chapter", { path, index });
}

/** 查询阅读进度。 */
export function fetchProgress(path: string): Promise<ReaderProgress | null> {
  return get<ReaderProgress | null>("/reader/progress", { path });
}

/** 保存阅读进度。 */
export function saveProgress(path: string, chapterIndex: number, percent: number): Promise<void> {
  return put<void>("/reader/progress", { path, chapterIndex, percent });
}
