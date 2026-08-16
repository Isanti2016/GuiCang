package com.guicang.nas.module.file;

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

  @Value("${guicang.file.max-upload-size-bytes:1073741824}")
  private long maxUploadSizeBytes;

  @Value("${guicang.file.blocked-extensions:}")
  private Set<String> blockedExtensions;

  @Value("${guicang.file.max-text-size-bytes:2097152}")
  private long maxTextSizeBytes;

  private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "txt", "markdown");

  public FileServiceImpl(
      StorageService storageService,
      DirPermissionService dirPermissionService,
      ThumbnailService thumbnailService) {
    this.storageService = storageService;
    this.dirPermissionService = dirPermissionService;
    this.thumbnailService = thumbnailService;
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
  }

  @Override
  @Audit(action = "file.rename", resource = "#path")
  public void rename(String path, String newName) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.rename(path, newName);
  }

  @Override
  @Audit(action = "file.move", resource = "#path")
  public void move(String path, String target) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    dirPermissionService.check(user.username(), authorities(), target, DirPerm.WRITE);
    storageService.move(path, target);
  }

  @Override
  @Audit(action = "file.delete", resource = "#path")
  public void delete(String path, boolean recursive) {
    AuthenticatedUser user = requireUser();
    dirPermissionService.check(user.username(), authorities(), path, DirPerm.WRITE);
    storageService.delete(path, recursive);
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
    return storageService.list(dirPath == null ? "." : dirPath).stream()
        .filter(e -> e.name().equals(filename))
        .findFirst()
        .orElseThrow(() -> new BizException("上传后未找到文件"));
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
    if (!"image".equals(FileTypeUtils.kind(name))) {
      throw new BizException("仅图片支持缩略图: " + path);
    }
    try {
      // 缓存键含大小与修改时间，文件变化自动重新生成
      String cacheKey =
          path + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis();
      Path thumb = thumbnailService.thumbnail(source, cacheKey);
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
