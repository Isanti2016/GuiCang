package com.guicang.nas.module.user;

import com.guicang.nas.module.user.dto.PermissionVO;
import java.util.List;

/** 权限点查询服务。 */
public interface PermissionService {

  /** 全部权限点列表。 */
  List<PermissionVO> listAll();
}
