package com.guicang.nas.module.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.favorite.dto.FavoriteVO;
import java.nio.file.Files;
import java.util.List;
import org.springframework.stereotype.Service;

/** 文件收藏服务实现。 */
@Service
public class FavoriteServiceImpl implements FavoriteService {

  private final FavoriteMapper favoriteMapper;
  private final StorageService storageService;

  public FavoriteServiceImpl(FavoriteMapper favoriteMapper, StorageService storageService) {
    this.favoriteMapper = favoriteMapper;
    this.storageService = storageService;
  }

  @Override
  public void add(String path) {
    AuthenticatedUser user = requireUser();
    if (path == null || path.isBlank() || !Files.exists(storageService.resolveFile(path))) {
      throw new BizException("收藏对象不存在: " + path);
    }
    Favorite existing = favoriteMapper.selectOne(
        new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getPath, path)
            .eq(Favorite::getUsername, user.username()));
    if (existing != null) {
      return;
    }
    Favorite f = new Favorite();
    f.setPath(path);
    f.setUsername(user.username());
    f.setCreatedAt(System.currentTimeMillis());
    favoriteMapper.insert(f);
  }

  @Override
  public void remove(String path) {
    AuthenticatedUser user = requireUser();
    favoriteMapper.delete(
        new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getPath, path)
            .eq(Favorite::getUsername, user.username()));
  }

  @Override
  public List<FavoriteVO> list() {
    AuthenticatedUser user = requireUser();
    return favoriteMapper.selectList(
        new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getUsername, user.username())
            .orderByDesc(Favorite::getCreatedAt))
        .stream()
        .map(f -> {
          String name = f.getPath().contains("/")
              ? f.getPath().substring(f.getPath().lastIndexOf('/') + 1)
              : f.getPath();
          return new FavoriteVO(f.getPath(), name, f.getCreatedAt());
        })
        .toList();
  }

  @Override
  public boolean isFavorite(String path) {
    AuthenticatedUser user = requireUser();
    return favoriteMapper.selectCount(
        new LambdaQueryWrapper<Favorite>()
            .eq(Favorite::getPath, path)
            .eq(Favorite::getUsername, user.username())) > 0;
  }

  private AuthenticatedUser requireUser() {
    return CurrentUserContext.currentUser()
        .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"));
  }
}
