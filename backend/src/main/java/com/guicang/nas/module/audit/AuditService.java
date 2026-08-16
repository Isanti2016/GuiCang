package com.guicang.nas.module.audit;

import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;

/** 审计记录服务。 */
public interface AuditService {

  /** 写入一条审计记录（创建时间由服务端填充）。 */
  void record(AuditLogCreateDTO dto);
}
