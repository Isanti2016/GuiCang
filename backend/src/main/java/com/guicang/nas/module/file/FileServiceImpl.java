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
import com.guicang.nas.module.file.dto.FileStreamInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 文件管理服务实现：目录操作与上传下载，每次操作校验登录与目录级权限，并审计留痕。 */
@Service
public class FileServiceImpl implements FileService {

  private final StorageService storageService;
  private final DirPermissionService dirPermissionService;
  private final ThumbnailService thumbnailService;
  private final FileIndexService fileIndexService;
  private final TrashItemMapper trashItemMapper;

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
      TrashItemMapper trashItemMapper) {
    this.storageService = storageService;
    this.dirPermissionService = dirPermissionService;
    this.thumbnailService = thumbnailService;
    this.fileIndexService = fileIndexService;
    this.trashItemMapper = trashItemMapper;
  }

  @Override
  public List<FileEntry> list(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    return storageService.list(path);
  }

  @Override
  @Audit(action = "file.mkdir", resource = "#path")
  public void mkdir(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.mkdir(path);
    indexDir(path, user.username());
  }

  @Override
  @Audit(action = "file.rename", resource = "#path")
  public void rename(String path, String newName) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.rename(path, newName);
    String newPath = path.substring(0, path.lastIndexOf('/') + 1) + newName;
    fileIndexService.rename(path, newPath);
  }

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

  @Override
  @Audit(action = "file.purge", resource = "#id")
  public void purgeTrash(Long id) {
    AuthenticatedUser user = requireUser();
    TrashItem item = requireTrashItem(id, user);
    storageService.delete(item.getTrashPath(), true);
    trashItemMapper.deleteById(id);
  }

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

  @Override
  public String readText(String path) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.READ);
    requireTextExtension(path);
    return storageService.readText(path, maxTextSizeBytes);
  }

  @Override
  @Audit(action = "file.write", resource = "#path")
  public void writeText(String path, String content) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    requireTextExtension(path);
    storageService.writeText(path, content);
    // 内容变更后重建索引（大小/时间戳刷新）
    FileEntry entry = findEntry(path);
    if (entry != null) {
      indexEntry(entry, user.username());
    }
  }

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
}
