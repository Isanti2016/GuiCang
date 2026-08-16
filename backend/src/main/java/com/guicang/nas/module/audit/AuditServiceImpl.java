package com.guicang.nas.module.audit;

import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;
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
}
