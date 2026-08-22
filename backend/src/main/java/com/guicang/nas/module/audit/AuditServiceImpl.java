package com.guicang.nas.module.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;
import java.util.List;
import org.springframework.stereotype.Service;

/** 审计记录服务实现。 */
@Service
public class AuditServiceImpl implements AuditService {

  private final AuditLogMapper auditLogMapper;

  public AuditServiceImpl(AuditLogMapper auditLogMapper) {
    this.auditLogMapper = auditLogMapper;
  }

  /**
   * 写入一条审计记录（创建时间由服务端填充）。
   *
   * @param dto 审计记录内容
   */
  @Override
  public void record(AuditLogCreateDTO dto) {
    AuditLog entity = new AuditLog();
    entity.setUsername(dto.username());
    entity.setAction(dto.action());
    entity.setResource(dto.resource());
    entity.setIp(dto.ip());
    entity.setUserAgent(dto.userAgent());
    entity.setResult(dto.result());
    entity.setDetail(dto.detail());
    entity.setCreatedAt(System.currentTimeMillis());
    auditLogMapper.insert(entity);
  }

  /**
   * 审计查询（分页 + 筛选）。
   *
   * @param username 按操作者筛选（可空）
   * @param action 按动作筛选（可空）
   * @param result 按结果筛选（可空）
   * @param page 页码（从 1 起）
   * @param size 每页条数（≤100）
   * @return 审计记录列表
   */
  @Override
  public List<AuditLog> query(String username, String action, String result, long page, long size) {
    return auditLogMapper.selectList(
        wrapper(username, action, result)
            .orderByDesc(AuditLog::getId)
            .last("LIMIT " + Math.min(size, 100) + " OFFSET " + (page - 1) * size));
  }

  /**
   * 总数（用于分页）。
   *
   * @param username 按操作者筛选（可空）
   * @param action 按动作筛选（可空）
   * @param result 按结果筛选（可空）
   * @return 匹配条件的记录总数
   */
  @Override
  public long count(String username, String action, String result) {
    return auditLogMapper.selectCount(wrapper(username, action, result));
  }

  private LambdaQueryWrapper<AuditLog> wrapper(String username, String action, String result) {
    return new LambdaQueryWrapper<AuditLog>()
        .eq(username != null && !username.isBlank(), AuditLog::getUsername, username)
        .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
        .eq(result != null && !result.isBlank(), AuditLog::getResult, result);
  }
}
