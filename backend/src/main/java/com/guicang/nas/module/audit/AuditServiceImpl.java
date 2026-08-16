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

  @Override
  public List<AuditLog> query(String username, String action, String result, long page, long size) {
    return auditLogMapper.selectList(
        wrapper(username, action, result)
            .orderByDesc(AuditLog::getId)
            .last("LIMIT " + Math.min(size, 100) + " OFFSET " + (page - 1) * size));
  }

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
