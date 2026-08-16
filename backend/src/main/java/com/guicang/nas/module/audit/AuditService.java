package com.guicang.nas.module.audit;

import com.guicang.nas.module.audit.dto.AuditLogCreateDTO;
import java.util.List;

/** 审计记录服务。 */
public interface AuditService {

  /** 写入一条审计记录（创建时间由服务端填充）。 */
  void record(AuditLogCreateDTO dto);

  /**
   * 审计查询（分页 + 筛选）。
   *
   * @param username 按操作者筛选（可空）
   * @param action 按动作筛选（可空）
   * @param result 按结果筛选（可空）
   * @param page 页码（从 1 起）
   * @param size 每页条数（≤100）
   */
  List<AuditLog> query(String username, String action, String result, long page, long size);

  /** 总数（用于分页）。 */
  long count(String username, String action, String result);
}
