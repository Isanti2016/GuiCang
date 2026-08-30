package com.guicang.nas.module.favorite;

import com.guicang.nas.module.favorite.dto.FavoriteVO;
import java.util.List;

/** 文件收藏服务。 */
public interface FavoriteService {

  /** 收藏文件/目录（已收藏则忽略）。 */
  void add(String path);

  /** 取消收藏。 */
  void remove(String path);

  /** 当前用户的收藏列表（倒序）。 */
  List<FavoriteVO> list();

  /** 是否已收藏。 */
  boolean isFavorite(String path);
}
