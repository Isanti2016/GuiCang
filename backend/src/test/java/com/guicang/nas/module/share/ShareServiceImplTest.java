package com.guicang.nas.module.share;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.file.dto.FileStreamInfo;
import com.guicang.nas.module.share.dto.ShareVO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 分享链接服务测试：创建/解析（过期/密码）/下载（文件与目录 zip）/撤销权限。 */
class ShareServiceImplTest {

  @TempDir Path dir;

  private ShareMapper mapper;
  private StorageService storageService;
  private ShareServiceImpl service;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(ShareMapper.class);
    storageService = Mockito.mock(StorageService.class);
    service = new ShareServiceImpl(mapper, storageService);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("admin", 1003L), null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private Path file(String rel, String content) throws Exception {
    Path abs = dir.resolve(rel);
    Files.createDirectories(abs.getParent());
    Files.writeString(abs, content);
    when(storageService.resolveFile(rel)).thenReturn(abs);
    return abs;
  }

  @Test
  void createFileShareNoPassword() throws Exception {
    file("books/小说.txt", "第一章\n正文。\n");
    when(mapper.insert(any(Share.class))).thenAnswer(inv -> {
      Share s = inv.getArgument(0);
      s.setId(1L);
      return 1;
    });

    ShareVO vo = service.create("books/小说.txt", null, null);

    assertNotNull(vo.token());
    assertEquals(32, vo.token().length());
    assertFalse(vo.hasPassword());
    assertNull(vo.expiresAt());
  }

  @Test
  void createWithPasswordAndExpiry() throws Exception {
    file("books/小说.txt", "x");
    when(mapper.insert(any(Share.class))).thenReturn(1);

    ShareVO vo = service.create("books/小说.txt", "secret", 3);

    assertTrue(vo.hasPassword());
    assertNotNull(vo.expiresAt());
    assertTrue(vo.expiresAt() > System.currentTimeMillis());
    assertTrue(vo.expiresAt() - System.currentTimeMillis() <= 3L * 86400_000L + 5000);
  }

  @Test
  void createMissingPathRejected() {
    when(storageService.resolveFile("nope.txt"))
        .thenThrow(new BizException("文件不存在: nope.txt"));
    assertThrows(BizException.class, () -> service.create("nope.txt", null, null));
  }

  @Test
  void resolveUnknownTokenRejected() {
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
    assertThrows(BizException.class, () -> service.resolve("deadbeef", null));
  }

  @Test
  void resolveExpiredRejected() {
    Share s = new Share();
    s.setToken("t");
    s.setExpiresAt(System.currentTimeMillis() - 1000);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);
    assertThrows(BizException.class, () -> service.resolve("t", null));
  }

  @Test
  void resolveWrongPasswordRejected() {
    Share s = new Share();
    s.setToken("t");
    s.setPassword("hash-of-correct");
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);
    assertThrows(BizException.class, () -> service.resolve("t", "wrong"));
  }

  @Test
  void resolveNoPasswordOk() {
    Share s = new Share();
    s.setToken("t");
    s.setPassword(null);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);
    assertNotNull(service.resolve("t", null));
  }

  @Test
  void downloadFile() throws Exception {
    Path abs = file("books/小说.txt", "内容");
    when(storageService.resolveFile("books/小说.txt")).thenReturn(abs);
    Share s = new Share();
    s.setToken("t");
    s.setPassword(null);
    s.setPath("books/小说.txt");
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);

    FileStreamInfo info = service.download("t", null);

    assertEquals("小说.txt", info.name());
    assertTrue(info.size() > 0);
  }

  @Test
  void downloadDirectoryZipped() throws Exception {
    Path dirAbs = dir.resolve("books");
    Files.createDirectories(dirAbs);
    Files.writeString(dirAbs.resolve("a.txt"), "A");
    when(storageService.resolveFile("books")).thenReturn(dirAbs);
    when(storageService.resolveFile("books/a.txt")).thenReturn(dirAbs.resolve("a.txt"));
    when(storageService.root()).thenReturn(dir);
    when(storageService.list("books"))
        .thenReturn(List.of(new FileEntry("a.txt", "books/a.txt", false, 1L, 0L, "txt")));
    Share s = new Share();
    s.setToken("t");
    s.setPassword(null);
    s.setPath("books");
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);

    FileStreamInfo info = service.download("t", null);

    assertTrue(info.name().endsWith(".zip"));
    assertTrue(Files.exists(info.path()));
  }

  @Test
  void revokeOwnShareDeletes() {
    Share s = new Share();
    s.setId(9L);
    s.setToken("t");
    s.setCreatedBy("admin");
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);

    service.revoke("t");

    verify(mapper).deleteById(9L);
  }

  @Test
  void revokeOthersShareRejected() {
    Share s = new Share();
    s.setId(9L);
    s.setToken("t");
    s.setCreatedBy("bob");
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(s);

    assertThrows(BizException.class, () -> service.revoke("t"));
    verify(mapper, never()).deleteById(any(Long.class));
  }

  @Test
  void listOwnShares() {
    Share s = new Share();
    s.setToken("t");
    s.setPath("books/小说.txt");
    s.setCreatedBy("admin");
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(s));

    List<ShareVO> list = service.list();

    assertEquals(1, list.size());
    assertEquals("books/小说.txt", list.get(0).path());
  }
}
