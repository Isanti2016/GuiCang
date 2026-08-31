package com.guicang.nas.module.favorite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.favorite.dto.FavoriteVO;
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

/** 文件收藏服务测试：CRUD、去重、不存在路径拒绝。 */
class FavoriteServiceImplTest {

  @TempDir Path dir;

  private FavoriteMapper mapper;
  private StorageService storageService;
  private FavoriteServiceImpl service;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(FavoriteMapper.class);
    storageService = Mockito.mock(StorageService.class);
    service = new FavoriteServiceImpl(mapper, storageService);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("admin", 1003L), null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void addInsertsFavorite() throws Exception {
    Path abs = dir.resolve("photo.jpg");
    Files.writeString(abs, "x");
    when(storageService.resolveFile("media/photo.jpg")).thenReturn(abs);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

    service.add("media/photo.jpg");

    verify(mapper).insert(any(Favorite.class));
  }

  @Test
  void addDuplicateIgnored() throws Exception {
    Path abs = dir.resolve("photo.jpg");
    Files.writeString(abs, "x");
    when(storageService.resolveFile("media/photo.jpg")).thenReturn(abs);
    Favorite existing = new Favorite();
    existing.setId(1L);
    when(mapper.selectOne(any(Wrapper.class))).thenReturn(existing);

    service.add("media/photo.jpg");

    verify(mapper, never()).insert(any(Favorite.class));
  }

  @Test
  void addMissingPathRejected() {
    when(storageService.resolveFile("nope.jpg"))
        .thenThrow(new BizException("文件不存在: nope.jpg"));
    assertThrows(BizException.class, () -> service.add("nope.jpg"));
  }

  @Test
  void removeDeletes() {
    service.remove("media/photo.jpg");
    verify(mapper).delete(any(Wrapper.class));
  }

  @Test
  void listMapsNameFromPath() {
    Favorite f = new Favorite();
    f.setPath("media/小说.txt");
    f.setUsername("admin");
    f.setCreatedAt(1000L);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(f));

    List<FavoriteVO> list = service.list();

    assertEquals(1, list.size());
    assertEquals("小说.txt", list.get(0).name());
    assertEquals("media/小说.txt", list.get(0).path());
  }

  @Test
  void isFavoriteTrueAndFalse() {
    when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
    assertTrue(service.isFavorite("media/photo.jpg"));
    when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    assertFalse(service.isFavorite("media/photo.jpg"));
  }
}
