package com.guicang.nas.module.share;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.infra.storage.FileTypeUtils;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.file.dto.FileStreamInfo;
import com.guicang.nas.module.share.dto.ShareVO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

/** 分享链接服务实现：生成/管理/免登录下载。 */
@Service
public class ShareServiceImpl implements ShareService {

  private final ShareMapper shareMapper;
  private final StorageService storageService;

  public ShareServiceImpl(ShareMapper shareMapper, StorageService storageService) {
    this.shareMapper = shareMapper;
    this.storageService = storageService;
  }

  @Override
  @Audit(action = "share.create", resource = "#path")
  public ShareVO create(String path, String password, Integer expireDays) {
    AuthenticatedUser user = requireUser();
    Path abs = storageService.resolveFile(path);
    if (!Files.exists(abs)) {
      throw new BizException("分享对象不存在: " + path);
    }
    Share share = new Share();
    share.setToken(generateToken());
    share.setPath(path);
    share.setPassword(password == null || password.isBlank() ? null : sha256(password));
    share.setExpiresAt(expireDays == null || expireDays <= 0
        ? null : System.currentTimeMillis() + expireDays * 86400_000L);
    share.setCreatedBy(user.username());
    share.setCreatedAt(System.currentTimeMillis());
    shareMapper.insert(share);
    return toVO(share);
  }

  @Override
  public List<ShareVO> list() {
    AuthenticatedUser user = requireUser();
    return shareMapper.selectList(
        new LambdaQueryWrapper<Share>()
            .eq(Share::getCreatedBy, user.username())
            .orderByDesc(Share::getCreatedAt))
        .stream().map(this::toVO).toList();
  }

  @Override
  @Audit(action = "share.revoke", resource = "#token")
  public void revoke(String token) {
    AuthenticatedUser user = requireUser();
    Share share = shareMapper.selectOne(
        new LambdaQueryWrapper<Share>().eq(Share::getToken, token));
    if (share == null) {
      return;
    }
    if (!user.username().equals(share.getCreatedBy())) {
      throw new BizException("无权限撤销他人的分享");
    }
    shareMapper.deleteById(share.getId());
  }

  @Override
  public Share resolve(String token, String password) {
    Share share = shareMapper.selectOne(
        new LambdaQueryWrapper<Share>().eq(Share::getToken, token));
    if (share == null) {
      throw new BizException("分享链接不存在或已失效");
    }
    if (share.getExpiresAt() != null && share.getExpiresAt() < System.currentTimeMillis()) {
      throw new BizException("分享链接已过期");
    }
    if (share.getPassword() != null) {
      if (password == null || !sha256(password).equals(share.getPassword())) {
        throw new BizException("访问密码错误");
      }
    }
    return share;
  }

  @Override
  public FileStreamInfo download(String token, String password) {
    Share share = resolve(token, password);
    Path abs = storageService.resolveFile(share.getPath());
    if (Files.isDirectory(abs)) {
      Path zip = buildZip(share.getPath());
      try {
        String name = share.getPath().contains("/")
            ? share.getPath().substring(share.getPath().lastIndexOf('/') + 1)
            : share.getPath();
        return new FileStreamInfo(zip, Files.size(zip), "application/zip", name + ".zip");
      } catch (IOException e) {
        throw new BizException("打包失败: " + e.getMessage());
      }
    }
    String name = abs.getFileName().toString();
    try {
      return new FileStreamInfo(abs, Files.size(abs), FileTypeUtils.contentType(name), name);
    } catch (IOException e) {
      throw new BizException("读取文件失败: " + share.getPath());
    }
  }

  private static final int ZIP_MAX_DEPTH = 8;

  private Path buildZip(String path) {
    Path tmpDir = storageService.root().resolve(".guicang-tmp");
    try {
      Files.createDirectories(tmpDir);
      Path zip = tmpDir.resolve("share-" + UUID.randomUUID() + ".zip");
      try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
        addToZip(zos, path, 0);
      }
      return zip;
    } catch (IOException e) {
      throw new BizException("打包失败: " + e.getMessage());
    }
  }

  private void addToZip(ZipOutputStream zos, String relPath, int depth) throws IOException {
    if (depth > ZIP_MAX_DEPTH) {
      return;
    }
    Path abs = storageService.resolveFile(relPath);
    if (Files.isDirectory(abs)) {
      for (FileEntry entry : storageService.list(relPath)) {
        addToZip(zos, entry.path(), depth + 1);
      }
    } else {
      zos.putNextEntry(new ZipEntry(relPath));
      Files.copy(abs, zos);
      zos.closeEntry();
    }
  }

  private ShareVO toVO(Share share) {
    return new ShareVO(share.getToken(), share.getPath(),
        share.getPassword() != null, share.getExpiresAt());
  }

  private String generateToken() {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    StringBuilder sb = new StringBuilder(32);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private String sha256(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new BizException("密码处理失败");
    }
  }

  private AuthenticatedUser requireUser() {
    return CurrentUserContext.currentUser()
        .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"));
  }
}
