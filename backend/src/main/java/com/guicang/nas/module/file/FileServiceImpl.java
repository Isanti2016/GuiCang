package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.infra.storage.FileTypeUtils;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.infra.thumbnail.ThumbnailService;
import com.guicang.nas.module.file.dto.ChunkStatus;
import com.guicang.nas.module.file.dto.DuplicateGroup;
import com.guicang.nas.module.file.dto.FileStreamInfo;
import com.guicang.nas.module.file.dto.MediaMetadataVO;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.UserService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 文件管理服务实现：目录操作与上传下载，每次操作校验登录与目录级权限，并审计留痕。 */
@Service
public class FileServiceImpl implements FileService {

  private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

  private final StorageService storageService;
  private final DirPermissionService dirPermissionService;
  private final ThumbnailService thumbnailService;
  private final FileIndexService fileIndexService;
  private final TrashItemMapper trashItemMapper;
  private final FileVersionMapper fileVersionMapper;
  private final UserService userService;
  private final MediaInspectService mediaInspectService;
  private final FileIndexMapper fileIndexMapper;

  @Value("${guicang.file.max-upload-size-bytes:1073741824}")
  private long maxUploadSizeBytes;

  @Value("${guicang.file.blocked-extensions:}")
  private Set<String> blockedExtensions;

  @Value("${guicang.file.max-text-size-bytes:2097152}")
  private long maxTextSizeBytes;

  private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "txt", "markdown");
  private static final String TRASH_DIR = ".guicang-trash";

  public FileServiceImpl(
      StorageService storageService,
      DirPermissionService dirPermissionService,
      ThumbnailService thumbnailService,
      FileIndexService fileIndexService,
      TrashItemMapper trashItemMapper,
      FileVersionMapper fileVersionMapper,
      UserService userService,
      MediaInspectService mediaInspectService,
      FileIndexMapper fileIndexMapper) {
    this.storageService = storageService;
    this.dirPermissionService = dirPermissionService;
    this.thumbnailService = thumbnailService;
    this.fileIndexService = fileIndexService;
    this.trashItemMapper = trashItemMapper;
    this.fileVersionMapper = fileVersionMapper;
    this.userService = userService;
    this.mediaInspectService = mediaInspectService;
    this.fileIndexMapper = fileIndexMapper;
  }

  /**
   * 目录列表（需 READ 权限）。
   *
   * @param path 存储根下相对路径
   * @return 目录下的文件条目列表
   */
  @Override
  public List<FileEntry> list(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    return storageService.list(path);
  }

  /**
   * 新建目录（需 WRITE 权限）。
   *
   * @param path 要创建的目录相对路径
   */
  @Override
  @Audit(action = "file.mkdir", resource = "#path")
  public void mkdir(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.mkdir(path);
    indexDir(path, user.username());
  }

  /**
   * 重命名（需 WRITE 权限）。
   *
   * @param path 原相对路径
   * @param newName 新名称
   */
  @Override
  @Audit(action = "file.rename", resource = "#path")
  public void rename(String path, String newName) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.rename(path, newName);
    String newPath = path.substring(0, path.lastIndexOf('/') + 1) + newName;
    fileIndexService.rename(path, newPath);
  }

  /**
   * 移动（源需 WRITE，目标目录需 WRITE）。
   *
   * @param path 源相对路径
   * @param target 目标目录（空或 . 表示根目录）
   */
  @Override
  @Audit(action = "file.move", resource = "#path")
  public void move(String path, String target) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    dirPermissionService.check(user.username(), authorities(), target, DirPerm.WRITE);
    storageService.move(path, target);
    String name = path.substring(path.lastIndexOf('/') + 1);
    String targetDir = target == null || target.isBlank() || ".".equals(target) ? "" : target;
    String newPath = targetDir.isBlank() ? name : targetDir + "/" + name;
    fileIndexService.rename(path, newPath);
  }

  /**
   * 删除（软删除：移入回收站，可恢复；需 WRITE 权限）。
   *
   * @param path 要删除的相对路径
   * @param recursive 是否递归删除（软删除实现下统一移入回收站，参数保留兼容）
   */
  @Override
  @Audit(action = "file.delete", resource = "#path")
  public void delete(String path, boolean recursive) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    // 软删除：移入回收站目录（.guicang-trash/），记录 trash_item 供恢复
    String name = path.contains("/") ? path.substring(path.lastIndexOf("/") + 1) : path;
    String trashPath = TRASH_DIR + "/" + System.currentTimeMillis() + "-" + name;
    storageService.moveTo(path, trashPath);
    TrashItem item = new TrashItem();
    item.setOriginalPath(path);
    item.setTrashPath(trashPath);
    item.setUsername(user.username());
    item.setKind(FileTypeUtils.kind(name));
    item.setSize(safeSize(path));
    item.setDeletedAt(System.currentTimeMillis());
    trashItemMapper.insert(item);
    fileIndexService.remove(path);
  }

  /**
   * 批量软删除文件/目录（移入回收站）。
   *
   * @param paths 相对路径列表
   * @param recursive 是否递归删除目录
   */
  @Override
  @Audit(action = "file.delete", resource = "#paths")
  public void deleteBatch(List<String> paths, boolean recursive) {
    for (String path : paths) {
      delete(path, recursive);
    }
  }

  /**
   * 自动清空过期回收站条目（定时任务调用，无需登录）。
   *
   * @param days 保留天数；days=0 表示不清理
   * @return 清理的条目数
   */
  @Override
  @Audit(action = "file.trash.auto", resource = "'自动清理(保留 ' + #days + ' 天)'")
  public int purgeExpiredTrash(int days) {
    if (days <= 0) {
      return 0;
    }
    long threshold = System.currentTimeMillis() - days * 24L * 3600 * 1000;
    List<TrashItem> expired =
        trashItemMapper.selectList(
            new LambdaQueryWrapper<TrashItem>()
                .lt(TrashItem::getDeletedAt, threshold)
                .isNotNull(TrashItem::getDeletedAt));
    for (TrashItem item : expired) {
      try {
        storageService.delete(item.getTrashPath(), true);
      } catch (Exception e) {
        log.warn(
            "自动清理回收站条目失败, id={}, path={}: {}", item.getId(), item.getTrashPath(), e.getMessage());
      }
      trashItemMapper.deleteById(item.getId());
    }
    if (!expired.isEmpty()) {
      log.info("回收站自动清理完成，共 {} 条（保留 {} 天）", expired.size(), days);
    }
    return expired.size();
  }

  /**
   * 回收站列表（管理员可见全部，普通用户仅本人）。
   *
   * @return 回收站条目列表
   */
  @Override
  public List<TrashItem> listTrash() {
    AuthenticatedUser user = requireUser();
    if (isAdmin(user)) {
      return trashItemMapper.selectList(
          new LambdaQueryWrapper<TrashItem>().orderByDesc(TrashItem::getDeletedAt));
    }
    return trashItemMapper.selectList(
        new LambdaQueryWrapper<TrashItem>()
            .eq(TrashItem::getUsername, user.username())
            .orderByDesc(TrashItem::getDeletedAt));
  }

  /**
   * 恢复回收站条目到原路径（原位置被占用时提示）。
   *
   * @param id 回收站条目 ID
   */
  @Override
  @Audit(action = "file.restore", resource = "#id")
  public void restoreTrash(Long id) {
    AuthenticatedUser user = requireUser();
    TrashItem item = requireTrashItem(id, user);
    // 原位置已存在时 moveTo 会拒绝，转成更明确的提示（避免覆盖）
    try {
      storageService.moveTo(item.getTrashPath(), item.getOriginalPath());
    } catch (BizException e) {
      if (e.getMessage().contains("目标已存在")) {
        throw new BizException("原位置已存在同名项，请先处理再恢复: " + item.getOriginalPath());
      }
      throw e;
    }
    trashItemMapper.deleteById(item.getId());
    // 重建索引
    FileEntry entry = findEntry(item.getOriginalPath());
    if (entry != null) {
      indexEntry(entry, item.getUsername());
    }
  }

  /**
   * 彻底删除回收站条目（不可恢复）。
   *
   * @param id 回收站条目 ID
   */
  @Override
  @Audit(action = "file.purge", resource = "#id")
  public void purgeTrash(Long id) {
    AuthenticatedUser user = requireUser();
    TrashItem item = requireTrashItem(id, user);
    storageService.delete(item.getTrashPath(), true);
    trashItemMapper.deleteById(id);
  }

  /** 清空回收站（管理员或本人）。 */
  @Override
  @Audit(action = "file.trash.empty")
  public void emptyTrash() {
    AuthenticatedUser user = requireUser();
    List<TrashItem> items =
        isAdmin(user)
            ? trashItemMapper.selectList(null)
            : trashItemMapper.selectList(
                new LambdaQueryWrapper<TrashItem>().eq(TrashItem::getUsername, user.username()));
    for (TrashItem item : items) {
      storageService.delete(item.getTrashPath(), true);
      trashItemMapper.deleteById(item.getId());
    }
  }

  private TrashItem requireTrashItem(Long id, AuthenticatedUser user) {
    TrashItem item = trashItemMapper.selectById(id);
    if (item == null) {
      throw new BizException("回收站条目不存在");
    }
    if (!isAdmin(user) && !item.getUsername().equals(user.username())) {
      throw new BizException("无权限操作他人回收站条目");
    }
    return item;
  }

  private boolean isAdmin(AuthenticatedUser user) {
    return user.username().equals("admin") || authorities().contains("ROLE_ADMIN");
  }

  private long safeSize(String path) {
    try {
      return Files.size(storageService.resolveFile(path));
    } catch (Exception e) {
      return 0L;
    }
  }

  /**
   * 上传文件到目录（需 WRITE 权限；校验大小与扩展名；流式写盘 + 原子改名）。
   *
   * @param dirPath 目标目录（空或 . 表示根目录）
   * @param file 上传的文件
   * @return 上传后的文件条目
   */
  @Override
  @Audit(action = "file.upload", resource = "#dirPath + '/' + #file.originalFilename")
  public FileEntry upload(String dirPath, MultipartFile file) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), dirPath, DirPerm.WRITE);
    if (file == null || file.isEmpty()) {
      throw new BizException("上传文件为空");
    }
    String filename = file.getOriginalFilename();
    if (filename == null
        || filename.isBlank()
        || filename.contains("/")
        || filename.contains("\\")) {
      throw new BizException("文件名不合法");
    }
    if (file.getSize() > maxUploadSizeBytes) {
      throw new BizException("文件超过大小上限（1G）");
    }
    checkQuota(user.username(), file.getSize());
    if (FileTypeUtils.isBlocked(filename, blockedExtensions)) {
      throw new BizException("不允许上传该类型文件: " + filename);
    }
    String target = dirPath == null || dirPath.isBlank() ? filename : dirPath + "/" + filename;
    try {
      storageService.upload(target, file.getInputStream(), file.getSize());
    } catch (IOException e) {
      throw new BizException("上传失败: " + filename);
    }
    FileEntry entry =
        storageService.list(dirPath == null ? "." : dirPath).stream()
            .filter(e -> e.name().equals(filename))
            .findFirst()
            .orElseThrow(() -> new BizException("上传后未找到文件"));
    indexEntry(entry, user.username());
    return entry;
  }

  /**
   * 文件流信息（需 READ 权限；用于下载/预览 Range 响应）。
   *
   * @param path 文件相对路径
   * @return 文件流信息（含绝对路径、大小与 Content-Type）
   */
  @Override
  public FileStreamInfo stream(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    Path file = storageService.resolveFile(path);
    try {
      String name = file.getFileName().toString();
      return new FileStreamInfo(file, Files.size(file), FileTypeUtils.contentType(name), name);
    } catch (IOException e) {
      throw new BizException("读取文件信息失败: " + path);
    }
  }

  /**
   * 获取媒体元数据。命中缓存（file_index.audio_codec 非空）直接返回；否则调 ffprobe 探测并回写索引。
   *
   * @param path 相对路径
   * @return 媒体元数据
   */
  @Override
  public MediaMetadataVO mediaMetadata(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    Path file = storageService.resolveFile(path);
    FileIndex cached = fileIndexMapper.selectOne(
        new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getPath, path));
    if (cached != null
        && cached.getAudioCodec() != null
        && !cached.getAudioCodec().isBlank()
        && cached.getVideoCodec() != null
        && !cached.getVideoCodec().isBlank()) {
      long dur = cached.getDurationSec() == null ? 0L : cached.getDurationSec();
      Set<String> audioAll =
          cached.getAudioCodec().isBlank() ? java.util.Set.of() : java.util.Set.of(cached.getAudioCodec());
      java.util.List<String> audios = new java.util.ArrayList<>(audioAll);
      java.util.List<String> videos =
          cached.getVideoCodec().isBlank() ? java.util.List.of() : java.util.List.of(cached.getVideoCodec());
      Boolean ba = cached.getAudioCodec().isBlank() ? null : MediaInspectService.AUDIO_SUPPORTED.contains(cached.getAudioCodec());
      Boolean bv = cached.getVideoCodec().isBlank() ? null : MediaInspectService.VIDEO_SUPPORTED.contains(cached.getVideoCodec());
      return new MediaMetadataVO(
          cached.getExt() == null ? "" : cached.getExt(),
          cached.getVideoCodec(),
          cached.getAudioCodec(),
          audios,
          videos,
          dur,
          0,
          0,
          false,
          ba,
          bv);
    }
    MediaMetadataVO vo = mediaInspectService.probe(file);
    if (cached != null) {
      cached.setAudioCodec(vo.audioCodec());
      cached.setVideoCodec(vo.videoCodec());
      cached.setDurationSec(vo.durationSec() == 0 ? null : vo.durationSec());
      fileIndexMapper.updateById(cached);
    }
    return vo;
  }

  /**
   * 读取文本内容（需 READ 权限；限大小）。
   *
   * @param path 文本文件相对路径
   * @return 文本内容
   */
  @Override
  public String readText(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    requireTextExtension(path);
    return storageService.readText(path, maxTextSizeBytes);
  }

  /**
   * 保存文本内容（需 WRITE 权限；仅允许 md/txt/markdown 扩展名）。
   *
   * @param path 文本文件相对路径
   * @param content 文本内容
   */
  @Override
  @Audit(action = "file.write", resource = "#path")
  public void writeText(String path, String content) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    requireTextExtension(path);
    saveVersion(path, user.username());
    storageService.writeText(path, content);
    // 内容变更后重建索引（大小/时间戳刷新）
    FileEntry entry = findEntry(path);
    if (entry != null) {
      indexEntry(entry, user.username());
    }
  }

  /**
   * 按名称/路径关键字搜索（索引查询 + 权限过滤）。
   *
   * @param keyword 搜索关键字
   * @return 匹配且当前用户可读的文件条目列表
   */
  @Override
  public List<FileEntry> search(String keyword) {
    AuthenticatedUser user = requireUser();
    return fileIndexService.search(keyword).stream()
        .filter(
            idx ->
                dirPermissionService.has(
                    user.username(), authorities(), idx.getPath(), DirPerm.READ))
        .map(
            idx ->
                new FileEntry(
                    idx.getName(),
                    idx.getPath(),
                    "dir".equals(idx.getKind()),
                    idx.getSize() == null ? 0 : idx.getSize(),
                    idx.getMtime() == null ? 0 : idx.getMtime(),
                    idx.getKind()))
        .toList();
  }

  /**
   * 全文检索（匹配 md/txt 内容 + 权限过滤）。
   *
   * @param keyword 搜索关键字
   * @return 匹配且当前用户可读的文件条目列表
   */
  @Override
  public List<FileEntry> searchContent(String keyword) {
    AuthenticatedUser user = requireUser();
    return fileIndexService.searchContent(keyword).stream()
        .filter(
            idx ->
                dirPermissionService.has(
                    user.username(), authorities(), idx.getPath(), DirPerm.READ))
        .map(
            idx ->
                new FileEntry(
                    idx.getName(),
                    idx.getPath(),
                    "dir".equals(idx.getKind()),
                    idx.getSize() == null ? 0 : idx.getSize(),
                    idx.getMtime() == null ? 0 : idx.getMtime(),
                    idx.getKind()))
        .toList();
  }

  /**
   * 递归收集目录下图片/视频（相册数据源；需 READ 权限；限制深度与数量）。
   *
   * @param path 起始目录相对路径
   * @return 收集到的图片/视频条目列表
   */
  @Override
  public List<FileEntry> media(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    List<FileEntry> result = new java.util.ArrayList<>();
    collectMedia(path, 0, result, user.username());
    return result;
  }

  private static final int MEDIA_MAX_DEPTH = 8;
  private static final int MEDIA_MAX_COUNT = 2000;

  private void collectMedia(String dir, int depth, List<FileEntry> out, String username) {
    if (depth > MEDIA_MAX_DEPTH || out.size() >= MEDIA_MAX_COUNT) {
      return;
    }
    for (FileEntry entry : storageService.list(dir)) {
      if (out.size() >= MEDIA_MAX_COUNT) {
        return;
      }
      if (!dirPermissionService.has(username, authorities(), entry.path(), DirPerm.READ)) {
        continue;
      }
      if (entry.dir()) {
        // 跳过回收站目录（相册不应展示已删除内容）
        if (TRASH_DIR.equals(entry.name()) || entry.path().startsWith(TRASH_DIR + "/")) {
          continue;
        }
        collectMedia(entry.path(), depth + 1, out, username);
      } else if ("image".equals(entry.kind()) || "video".equals(entry.kind())) {
        out.add(entry);
      }
    }
  }

  private FileEntry findEntry(String path) {
    String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ".";
    String name = path.substring(path.lastIndexOf('/') + 1);
    return storageService.list(dir).stream()
        .filter(e -> e.name().equals(name))
        .findFirst()
        .orElse(null);
  }

  private void indexEntry(FileEntry entry, String owner) {
    fileIndexService.upsert(
        entry.path(), entry.name(), entry.kind(), entry.size(), entry.mtime(), owner);
    if ("note".equals(entry.kind())) {
      try {
        fileIndexService.updateContent(
            entry.path(), storageService.readText(entry.path(), maxTextSizeBytes));
      } catch (Exception e) {
        log.debug("全文索引内容读取失败: {}", entry.path());
      }
    }
  }

  private void indexDir(String path, String owner) {
    FileEntry entry = findEntry(path);
    if (entry != null) {
      indexEntry(entry, owner);
    }
  }

  private void requireTextExtension(String path) {
    String lower = path.toLowerCase();
    boolean allowed = TEXT_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext));
    if (!allowed) {
      throw new BizException("仅支持编辑 md/txt/markdown 文本文件");
    }
  }

  /**
   * 图片缩略图（需 READ 权限；懒生成 + 磁盘缓存，返回缩略图流信息）。
   *
   * @param path 图片/视频文件相对路径
   * @return 缩略图流信息
   */
  @Override
  public FileStreamInfo thumbnail(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    Path source = storageService.resolveFile(path);
    String name = source.getFileName().toString();
    String kind = FileTypeUtils.kind(name);
    try {
      // 缓存键含大小与修改时间，文件变化自动重新生成
      String cacheKey =
          path + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis();
      Path thumb;
      if ("image".equals(kind)) {
        thumb = thumbnailService.thumbnail(source, cacheKey);
      } else if ("video".equals(kind)) {
        thumb = thumbnailService.videoThumbnail(source, cacheKey);
      } else {
        throw new BizException("仅图片/视频支持缩略图: " + path);
      }
      return new FileStreamInfo(thumb, Files.size(thumb), "image/jpeg", "thumb.jpg");
    } catch (IOException e) {
      throw new BizException("读取缩略图失败: " + path);
    }
  }

  /**
   * 查找重复文件（相同大小 + 相同 SHA-256 哈希）。
   */
  @Override
  @Audit(action = "file.duplicates", resource = "#path")
  public List<DuplicateGroup> findDuplicates(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    List<FileEntry> files = new ArrayList<>();
    collectAllFiles(path, 0, files, user.username());
    Map<Long, List<FileEntry>> bySize = new HashMap<>();
    for (FileEntry f : files) {
      if (f.size() > 0) {
        bySize.computeIfAbsent(f.size(), k -> new ArrayList<>()).add(f);
      }
    }
    List<DuplicateGroup> result = new ArrayList<>();
    for (Map.Entry<Long, List<FileEntry>> e : bySize.entrySet()) {
      if (e.getValue().size() < 2) {
        continue;
      }
      Map<String, List<FileEntry>> byHash = new HashMap<>();
      for (FileEntry f : e.getValue()) {
        byHash.computeIfAbsent(sha256File(f.path()), k -> new ArrayList<>()).add(f);
      }
      for (Map.Entry<String, List<FileEntry>> h : byHash.entrySet()) {
        if (h.getValue().size() >= 2) {
          result.add(new DuplicateGroup(e.getKey(), h.getKey(), h.getValue()));
        }
      }
    }
    return result;
  }

  private static final int DUP_MAX_DEPTH = 10;
  private static final int DUP_MAX_COUNT = 50000;

  private void collectAllFiles(String dir, int depth, List<FileEntry> out, String username) {
    if (depth > DUP_MAX_DEPTH || out.size() >= DUP_MAX_COUNT) {
      return;
    }
    for (FileEntry entry : storageService.list(dir)) {
      if (out.size() >= DUP_MAX_COUNT) {
        return;
      }
      if (TRASH_DIR.equals(entry.name()) || entry.path().startsWith(TRASH_DIR + "/")) {
        continue;
      }
      if (!dirPermissionService.has(username, authorities(), entry.path(), DirPerm.READ)) {
        continue;
      }
      if (entry.dir()) {
        collectAllFiles(entry.path(), depth + 1, out, username);
      } else {
        out.add(entry);
      }
    }
  }

  private String sha256File(String path) {
    try (InputStream in = Files.newInputStream(storageService.resolveFile(path))) {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) {
        md.update(buf, 0, n);
      }
      StringBuilder sb = new StringBuilder(64);
      for (byte b : md.digest()) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * 某文件的历史版本列表（倒序）。
   */
  @Override
  public List<FileVersion> listVersions(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    return fileVersionMapper.selectList(
        new LambdaQueryWrapper<FileVersion>()
            .eq(FileVersion::getPath, path)
            .orderByDesc(FileVersion::getCreatedAt));
  }

  /**
   * 回滚到指定历史版本。
   */
  @Override
  @Audit(action = "file.restore-version", resource = "#id")
  public void restoreVersion(Long id) {
    AuthenticatedUser user = requireUser();
    FileVersion v = fileVersionMapper.selectById(id);
    if (v == null) {
      throw new BizException("历史版本不存在");
    }
    dirPermissionService.check(user.username(), authorities(), v.getPath(), DirPerm.WRITE);
    saveVersion(v.getPath(), user.username());
    storageService.writeText(v.getPath(), v.getContent());
    FileEntry entry = findEntry(v.getPath());
    if (entry != null) {
      indexEntry(entry, user.username());
    }
  }

  private static final int MAX_VERSIONS_PER_FILE = 20;

  private void saveVersion(String path, String username) {
    try {
      String old = storageService.readText(path, maxTextSizeBytes);
      if (old == null || old.isBlank()) {
        return;
      }
      FileVersion v = new FileVersion();
      v.setPath(path);
      v.setContent(old);
      v.setSize((long) old.length());
      v.setCreatedBy(username);
      v.setCreatedAt(System.currentTimeMillis());
      fileVersionMapper.insert(v);
      trimVersions(path);
    } catch (Exception e) {
      log.debug("保存历史版本失败: {}", path);
    }
  }

  private void trimVersions(String path) {
    List<FileVersion> versions = fileVersionMapper.selectList(
        new LambdaQueryWrapper<FileVersion>()
            .eq(FileVersion::getPath, path)
            .orderByDesc(FileVersion::getCreatedAt));
    for (int i = MAX_VERSIONS_PER_FILE; i < versions.size(); i++) {
      fileVersionMapper.deleteById(versions.get(i).getId());
    }
  }

  private void checkQuota(String username, long newSize) {
    SysUser sysUser = userService.findByUsername(username).orElse(null);
    if (sysUser == null || sysUser.getQuotaBytes() == null || sysUser.getQuotaBytes() <= 0) {
      return;
    }
    String home = sysUser.getHomePath();
    if (home == null || home.isBlank()) {
      return;
    }
    long used = fileIndexService.sumSizeByPrefix(home);
    if (used + newSize > sysUser.getQuotaBytes()) {
      throw new BizException(
          "存储空间配额不足：已用 " + used + " 字节，配额 " + sysUser.getQuotaBytes() + " 字节");
    }
  }

  /**
   * 上传单个分片（大文件分片上传）。
   */
  @Override
  public void uploadChunk(
      String path, String filename, String uploadId, int chunkIndex, int totalChunks,
      MultipartFile file) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")) {
      throw new BizException("文件名不合法");
    }
    if (FileTypeUtils.isBlocked(filename, blockedExtensions)) {
      throw new BizException("不允许上传该类型文件: " + filename);
    }
    Path chunkDir = storageService.root().resolve(".guicang-tmp/chunks").resolve(uploadId);
    try {
      Files.createDirectories(chunkDir);
      Files.copy(file.getInputStream(), chunkDir.resolve(String.valueOf(chunkIndex)),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new BizException("分片上传失败: " + e.getMessage());
    }
  }

  @Override
  public ChunkStatus chunkStatus(String uploadId) {
    requireUser();
    if (uploadId == null || uploadId.isBlank()) {
      throw new BizException("uploadId 不能为空");
    }
    Path chunkDir = storageService.root().resolve(".guicang-tmp/chunks").resolve(uploadId);
    if (!Files.isDirectory(chunkDir)) {
      return new ChunkStatus(uploadId, List.of(), 0L);
    }
    List<Integer> uploaded = new ArrayList<>();
    long[] bytes = {0L};
    try (var stream = Files.list(chunkDir)) {
      stream.forEach(
          p -> {
            String name = p.getFileName().toString();
            if (name.matches("\\d+")) {
              try {
                uploaded.add(Integer.parseInt(name));
                bytes[0] += Files.size(p);
              } catch (IOException ignored) {
              }
            }
          });
    } catch (IOException e) {
      throw new BizException("查询分片状态失败: " + e.getMessage());
    }
    uploaded.sort(Comparator.naturalOrder());
    return new ChunkStatus(uploadId, uploaded, bytes[0]);
  }

  /**
   * 合并分片为完整文件。
   */
  @Override
  @Audit(action = "file.chunk-complete", resource = "#filename")
  public FileEntry completeChunkUpload(
      String path, String filename, String uploadId, int totalChunks) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    Path chunkDir = storageService.root().resolve(".guicang-tmp/chunks").resolve(uploadId);
    String target = path == null || path.isBlank() ? filename : path + "/" + filename;
    try {
      Path tmp = storageService.root().resolve(".guicang-tmp").resolve("merge-" + UUID.randomUUID());
      long totalSize = 0;
      try (OutputStream out = Files.newOutputStream(tmp)) {
        for (int i = 0; i < totalChunks; i++) {
          Path chunkFile = chunkDir.resolve(String.valueOf(i));
          if (!Files.exists(chunkFile)) {
            throw new BizException("分片不完整：缺少第 " + i + " 片");
          }
          totalSize += Files.size(chunkFile);
          Files.copy(chunkFile, out);
        }
      }
      checkQuota(user.username(), totalSize);
      Path targetAbs = storageService.root().resolve(target);
      if (targetAbs.getParent() != null) {
        Files.createDirectories(targetAbs.getParent());
      }
      Files.move(tmp, targetAbs, StandardCopyOption.REPLACE_EXISTING);
      deleteDir(chunkDir);
      FileEntry entry = findEntry(target);
      if (entry != null) {
        indexEntry(entry, user.username());
      }
      return entry;
    } catch (IOException e) {
      throw new BizException("合并分片失败: " + e.getMessage());
    }
  }

  private void deleteDir(Path dir) {
    try (var stream = Files.list(dir)) {
      stream.forEach(p -> {
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
      });
      Files.deleteIfExists(dir);
    } catch (IOException ignored) {
    }
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
  /**
   * 批量打包下载（zip）：校验 READ 权限后递归打包到临时 zip。
   */
  @Override
  @Audit(action = "file.zip", resource = "#paths")
  public FileStreamInfo zipDownload(List<String> paths) {
    AuthenticatedUser user = requireUser();
    if (paths == null || paths.isEmpty()) {
      throw new BizException("请选择要打包的文件或目录");
    }
    for (String p : paths) {
      dirPermissionService.check(user.username(), authorities(), p, DirPerm.READ);
    }
    Path zip = buildZip(paths);
    try {
      return new FileStreamInfo(zip, Files.size(zip), "application/zip", "guicang-download.zip");
    } catch (IOException e) {
      throw new BizException("打包失败: " + e.getMessage());
    }
  }

  private static final int ZIP_MAX_DEPTH = 8;

  private Path buildZip(List<String> paths) {
    Path tmpDir = storageService.root().resolve(".guicang-tmp");
    try {
      Files.createDirectories(tmpDir);
      cleanOldZips(tmpDir);
      Path zip = tmpDir.resolve("zip-" + UUID.randomUUID() + ".zip");
      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
        for (String p : paths) {
          addToZip(zos, p, 0);
        }
      }
      return zip;
    } catch (IOException e) {
      throw new BizException("打包失败: " + e.getMessage());
    }
  }

  private void cleanOldZips(Path tmpDir) {
    try (var stream = Files.list(tmpDir)) {
      stream.filter(p -> p.getFileName().toString().endsWith(".zip"))
          .filter(p -> {
            try {
              return Files.getLastModifiedTime(p).toMillis() < System.currentTimeMillis() - 3600_000L;
            } catch (IOException e) {
              return false;
            }
          })
          .forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException ignored) { }
          });
    } catch (IOException ignored) {
    }
  }

  private void addToZip(ZipOutputStream zos, String relPath, int depth) throws IOException {
    if (depth > ZIP_MAX_DEPTH) {
      return;
    }
    Path abs = storageService.resolveFile(relPath);
    if (Files.isDirectory(abs)) {
      for (FileEntry entry : storageService.list(relPath)) {
        if (TRASH_DIR.equals(entry.name()) || entry.path().startsWith(TRASH_DIR + "/")) {
          continue;
        }
        addToZip(zos, entry.path(), depth + 1);
      }
    } else {
      zos.putNextEntry(new ZipEntry(relPath));
      Files.copy(abs, zos);
      zos.closeEntry();
    }
  }
}
