package com.guicang.nas.module.user;

import com.guicang.nas.module.user.dto.PermissionVO;
import java.util.List;
import org.springframework.stereotype.Service;

/** 权限点查询服务实现。 */
@Service
public class PermissionServiceImpl implements PermissionService {

  private final SysPermissionMapper sysPermissionMapper;

  public PermissionServiceImpl(SysPermissionMapper sysPermissionMapper) {
    this.sysPermissionMapper = sysPermissionMapper;
  }

  /**
   * 全部权限点列表。
   *
   * @return 权限点列表
   */
  @Override
  public List<PermissionVO> listAll() {
    return sysPermissionMapper.selectList(null).stream()
        .map(
            p ->
                new PermissionVO(p.getId(), p.getCode(), p.getName(), p.getType(), p.getResource()))
        .toList();
  }
}
