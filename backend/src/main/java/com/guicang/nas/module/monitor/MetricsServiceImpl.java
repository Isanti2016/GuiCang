package com.guicang.nas.module.monitor;

import com.guicang.nas.infra.monitor.HostMetrics;
import com.guicang.nas.infra.monitor.MetricsCollector;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 监控指标服务实现：每 30s 采集一次（细粒度保留 2h=240 点）， 每 5min 沉淀一个粗粒度点（保留 24h=288 点），全部在内存。 */
@Service
public class MetricsServiceImpl implements MetricsService {

  private static final Logger log = LoggerFactory.getLogger(MetricsServiceImpl.class);
  private static final int FINE_CAPACITY = 240;
  private static final int COARSE_CAPACITY = 288;

  private final MetricsCollector metricsCollector;
  private final Deque<HostMetrics> fineSeries = new ArrayDeque<>(FINE_CAPACITY);
  private final Deque<HostMetrics> coarseSeries = new ArrayDeque<>(COARSE_CAPACITY);
  private volatile HostMetrics latest;

  public MetricsServiceImpl(MetricsCollector metricsCollector) {
    this.metricsCollector = metricsCollector;
  }

  @Scheduled(fixedRate = 30_000, initialDelay = 5_000)
  public void sampleFine() {
    try {
      HostMetrics metrics = metricsCollector.collect();
      latest = metrics;
      synchronized (fineSeries) {
        fineSeries.addLast(metrics);
        while (fineSeries.size() > FINE_CAPACITY) {
          fineSeries.removeFirst();
        }
      }
    } catch (Exception e) {
      log.warn("指标采样失败: {}", e.getMessage());
    }
  }

  @Scheduled(fixedRate = 300_000, initialDelay = 30_000)
  public void sampleCoarse() {
    HostMetrics metrics = latest;
    if (metrics == null) {
      return;
    }
    synchronized (coarseSeries) {
      coarseSeries.addLast(metrics);
      while (coarseSeries.size() > COARSE_CAPACITY) {
        coarseSeries.removeFirst();
      }
    }
  }

  @Override
  public HostMetrics latest() {
    return latest;
  }

  @Override
  public List<SeriesPoint> series(String metric, String granularity) {
    boolean fine = "fine".equalsIgnoreCase(granularity);
    List<HostMetrics> source;
    synchronized (fineSeries) {
      source = fine ? List.copyOf(fineSeries) : List.copyOf(coarseSeries);
    }
    return source.stream().map(m -> new SeriesPoint(m.timestamp(), extract(metric, m))).toList();
  }

  private double extract(String metric, HostMetrics m) {
    return switch (metric == null ? "" : metric) {
      case "cpu" -> m.cpu();
      case "mem" -> percent(m.memTotalKb(), m.memAvailKb());
      case "disk" -> percent(m.diskTotalKb(), m.diskAvailKb());
      case "net" -> m.netRxBytes() + m.netTxBytes();
      default -> m.cpu();
    };
  }

  private double percent(long total, long avail) {
    if (total <= 0) {
      return 0;
    }
    return (total - avail) * 100.0 / total;
  }
}
