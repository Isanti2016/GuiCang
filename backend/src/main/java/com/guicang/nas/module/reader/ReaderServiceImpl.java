package com.guicang.nas.module.reader;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.file.DirPerm;
import com.guicang.nas.module.file.DirPermissionService;
import com.guicang.nas.module.reader.dto.ReaderProgressVO;
import com.guicang.nas.module.reader.dto.SaveProgressRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 小说阅读服务实现：每次读取校验 READ 权限；章节索引按文件缓存（带 mtime 失效）。 */
@Service
public class ReaderServiceImpl implements ReaderService {

  /** 缓存书籍数量上限。 */
  private static final int CACHE_LIMIT = 32;

  /** 打开书籍的文件大小上限：500MB。 */
  private static final long MAX_FILE_BYTES = 500L * 1024 * 1024;

  private final StorageService storageService;
  private final DirPermissionService dirPermissionService;
  private final ReadingProgressMapper progressMapper;
  private final TxtNovelParser txtParser;
  private final EpubNovelParser epubParser;

  /** 书籍解析缓存：path → 解析结果（最近使用优先淘汰）。 */
  private final Map<String, CachedNovel> cache =
      Collections.synchronizedMap(
          new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedNovel> eldest) {
              return size() > CACHE_LIMIT;
            }
          });

  public ReaderServiceImpl(
      StorageService storageService,
      DirPermissionService dirPermissionService,
      ReadingProgressMapper progressMapper,
      TxtNovelParser txtParser,
      EpubNovelParser epubParser) {
    this.storageService = storageService;
    this.dirPermissionService = dirPermissionService;
    this.progressMapper = progressMapper;
    this.txtParser = txtParser;
    this.epubParser = epubParser;
  }

  @Override
  public NovelMeta open(String path) {
    Path file = requireReadable(path);
    CachedNovel cached = getCached(path, file);
    if (cached instanceof CachedTxt t) {
      List<NovelChapter> chapters =
          t.parsed().chapters().stream()
              .map(c -> new NovelChapter(c.index(), c.title()))
              .toList();
      return new NovelMeta(
          path,
          TxtNovelParser.titleFromFileName(file),
          null,
          NovelFormat.TXT,
          t.parsed().charset().name(),
          t.size(),
          chapters.size(),
          chapters);
    }
    CachedEpub e = (CachedEpub) cached;
    List<NovelChapter> chapters =
        e.parsed().chapters().stream()
            .map(c -> new NovelChapter(c.index(), c.title()))
            .toList();
    return new NovelMeta(
        path,
        e.parsed().title(),
        e.parsed().author(),
        NovelFormat.EPUB,
        "UTF-8",
        e.size(),
        chapters.size(),
        chapters);
  }

  @Override
  public ChapterContent chapter(String path, int index) {
    Path file = requireReadable(path);
    CachedNovel cached = getCached(path, file);
    if (cached instanceof CachedTxt t) {
      try {
        String content = txtParser.readChapter(file, t.parsed(), index);
        return new ChapterContent(
            path,
            index,
            t.parsed().chapters().size(),
            t.parsed().chapters().get(index).title(),
            content);
      } catch (IOException ex) {
        throw new BizException("读取章节失败: " + path);
      }
    }
    CachedEpub e = (CachedEpub) cached;
    if (index < 0 || index >= e.parsed().chapters().size()) {
      throw new BizException("章节索引越界");
    }
    EpubNovelParser.EpubChapter c = e.parsed().chapters().get(index);
    return new ChapterContent(path, index, e.parsed().chapters().size(), c.title(), c.content());
  }

  @Override
  public ReaderProgressVO progress(String path) {
    String username = requireUser().username();
    ReadingProgress p = findProgress(username, path);
    if (p == null) {
      return null;
    }
    return new ReaderProgressVO(p.getFilePath(), p.getChapterIndex(), p.getPercent(), p.getUpdatedAt());
  }

  @Override
  public void saveProgress(SaveProgressRequest request) {
    AuthenticatedUser user = requireUser();
    requireReadable(request.path());
    ReadingProgress p = findProgress(user.username(), request.path());
    long now = System.currentTimeMillis();
    if (p == null) {
      p = new ReadingProgress();
      p.setUsername(user.username());
      p.setFilePath(request.path());
      p.setChapterIndex(request.chapterIndex());
      p.setPercent(request.percent());
      p.setUpdatedAt(now);
      progressMapper.insert(p);
    } else {
      p.setChapterIndex(request.chapterIndex());
      p.setPercent(request.percent());
      p.setUpdatedAt(now);
      progressMapper.updateById(p);
    }
  }

  /** 校验 READ 权限并返回文件绝对路径；同时校验支持格式。 */
  private Path requireReadable(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    Path file = storageService.resolveFile(path);
    String name = file.getFileName().toString();
    if (!txtParser.supports(name) && !epubParser.supports(name)) {
      throw new BizException("暂不支持该格式阅读（仅支持 .txt / .epub 小说文件）");
    }
    try {
      if (Files.size(file) > MAX_FILE_BYTES) {
        throw new BizException("文件过大（>500MB），暂不支持阅读");
      }
    } catch (IOException ex) {
      throw new BizException("读取文件信息失败: " + path);
    }
    return file;
  }

  /** 取缓存，miss 时解析并放入。 */
  private CachedNovel getCached(String path, Path file) {
    synchronized (cache) {
      CachedNovel cached = cache.get(path);
      long mtime;
      long size;
      try {
        mtime = Files.getLastModifiedTime(file).toMillis();
        size = Files.size(file);
      } catch (IOException ex) {
        throw new BizException("读取文件信息失败: " + path);
      }
      if (cached != null && cached.lastModified() == mtime && cached.size() == size) {
        return cached;
      }
      String name = file.getFileName().toString();
      try {
        if (txtParser.supports(name)) {
          CachedTxt fresh = new CachedTxt(mtime, size, txtParser.parse(file));
          cache.put(path, fresh);
          return fresh;
        }
        CachedEpub fresh = new CachedEpub(mtime, size, epubParser.parse(file));
        cache.put(path, fresh);
        return fresh;
      } catch (IOException ex) {
        throw new BizException("解析书籍失败: " + path);
      }
    }
  }

  private ReadingProgress findProgress(String username, String path) {
    return progressMapper.selectOne(
        new LambdaQueryWrapper<ReadingProgress>()
            .eq(ReadingProgress::getUsername, username)
            .eq(ReadingProgress::getFilePath, path));
  }

  private AuthenticatedUser requireUser() {
    return CurrentUserContext.currentUser()
        .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"));
  }

  private List<String> authorities() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return List.of();
    }
    return authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
  }

  /** 缓存条目：解析结果 + 文件指纹。 */
  private interface CachedNovel {
    long lastModified();

    long size();
  }

  private record CachedTxt(long lastModified, long size, TxtNovelParser.ParsedTxt parsed)
      implements CachedNovel {}

  private record CachedEpub(long lastModified, long size, EpubNovelParser.ParsedEpub parsed)
      implements CachedNovel {}
}
