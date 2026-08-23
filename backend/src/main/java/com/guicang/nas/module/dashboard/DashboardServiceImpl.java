package com.guicang.nas.module.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.infra.monitor.HostMetrics;
import com.guicang.nas.module.audit.AuditLog;
import com.guicang.nas.module.audit.AuditLogMapper;
import com.guicang.nas.module.dashboard.DashboardSummary.RecentOperation;
import com.guicang.nas.module.dashboard.DashboardSummary.UserStorageUsage;
import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexMapper;
import com.guicang.nas.module.monitor.MetricsService;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 大屏聚合服务实现：磁盘、文件索引统计、用户数、最近审计操作。 */
@Service
public class DashboardServiceImpl implements DashboardService {

  private final MetricsService metricsService;
  private final FileIndexMapper fileIndexMapper;
  private final SysUserMapper sysUserMapper;
  private final AuditLogMapper auditLogMapper;

  public DashboardServiceImpl(
      MetricsService metricsService,
      FileIndexMapper fileIndexMapper,
      SysUserMapper sysUserMapper,
      AuditLogMapper auditLogMapper) {
    this.metricsService = metricsService;
    this.fileIndexMapper = fileIndexMapper;
    this.sysUserMapper = sysUserMapper;
    this.auditLogMapper = auditLogMapper;
  }

  /**
   * 大屏聚合：存储/文件统计/用户数/最近操作。
   *
   * @return 大屏聚合数据
   */
  @Override
  public DashboardSummary summary() {
    HostMetrics latest = metricsService.latest();
    long diskTotal = latest == null ? 0 : latest.diskTotalKb();
    long diskAvail = latest == null ? 0 : latest.diskAvailKb();
    long diskUsedPercent = diskTotal > 0 ? (diskTotal - diskAvail) * 100 / diskTotal : 0;

    Map<String, Long> kindCounts = countByKind();
    long userTotal = sysUserMapper.selectCount(null);
    long userEnabled =
        sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEnabled, 1));

    List<RecentOperation> recent =
        auditLogMapper
            .selectList(
                new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getId).last("LIMIT 10"))
            .stream()
            .map(
                a ->
                    new RecentOperation(
                        a.getId(),
                        a.getUsername(),
                        a.getAction(),
                        a.getResource(),
                        a.getResult(),
                        a.getCreatedAt()))
            .toList();

    return new DashboardSummary(
        diskTotal,
        diskAvail,
        diskUsedPercent,
        kindCounts.values().stream().mapToLong(Long::longValue).sum(),
        kindCounts.getOrDefault("dir", 0L),
        kindCounts.getOrDefault("image", 0L),
        kindCounts.getOrDefault("video", 0L),
        kindCounts.getOrDefault("note", 0L),
        userTotal,
        userEnabled,
        recent,
        userStorageUsage());
  }

  /** 用户存储占用：按 personal/<user>/ 前缀从 file_index 聚合大小与数量（仅取所需列，避免全行传输）。 */
  private List<UserStorageUsage> userStorageUsage() {
    java.util.Map<String, long[]> usage = new java.util.TreeMap<>();
    fileIndexMapper
        .selectList(
            new LambdaQueryWrapper<FileIndex>()
                .select(FileIndex::getPath, FileIndex::getSize)
                .likeRight(FileIndex::getPath, "personal/"))
        .forEach(
            idx -> {
              String rel = idx.getPath();
              int slash = rel.indexOf('/', "personal/".length());
              if (slash < 0) {
                return;
              }
              String user = rel.substring("personal/".length(), slash);
              long[] acc = usage.computeIfAbsent(user, k -> new long[2]);
              acc[0] += idx.getSize() == null ? 0 : idx.getSize();
              acc[1] += 1;
            });
    return usage.entrySet().stream()
        .map(e -> new UserStorageUsage(e.getKey(), e.getValue()[0], e.getValue()[1]))
        .toList();
  }

  /**
   * 按类型统计文件数量（SQL 侧 GROUP BY 聚合，避免全表拉取到内存）。
   *
   * @return 类型码 → 数量
   */
  private Map<String, Long> countByKind() {
    Map<String, Long> counts =
        new EnumMap<>(Kind.class)
            .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getKey().code, e -> 0L));
    fileIndexMapper
        .selectList(
            new LambdaQueryWrapper<FileIndex>()
                .select(FileIndex::getKind)
                .groupBy(FileIndex::getKind))
        .forEach(idx -> counts.merge(idx.getKind(), 1L, Long::sum));
    return counts;
  }

  private enum Kind {
    DIR("dir"),
    IMAGE("image"),
    VIDEO("video"),
    NOTE("note");

    final String code;

    Kind(String code) {
      this.code = code;
    }
  }
}
