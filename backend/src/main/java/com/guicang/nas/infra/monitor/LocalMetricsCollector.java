package com.guicang.nas.infra.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地指标采集：直接读 /proc（本机开发模式，能取到宿主机真实指标）。
 *
 * <p>容器部署时必须用 helper（容器内 /proc 非宿主）；配置 guicang.metrics.source=helper 切换。
 */
@Component
@ConditionalOnProperty(
    name = "guicang.metrics.source",
    havingValue = "local",
    matchIfMissing = true)
public class LocalMetricsCollector implements MetricsCollector {

  private static final Logger log = LoggerFactory.getLogger(LocalMetricsCollector.class);

  @Value("${guicang.storage.root}")
  private String storageRoot;

  @Override
  public HostMetrics collect() {
    try {
      long[] cpu = readCpu();
      long[] mem = readMemInfo();
      long[] swap = readSwap();
      long[] disk = readDisk();
      long[] net = readNet();
      String[] load = readLoad();
      long uptime = readUptime();
      return new HostMetrics(
          cpu[0] / 10.0,
          Double.parseDouble(load[0]),
          Double.parseDouble(load[1]),
          Double.parseDouble(load[2]),
          mem[0],
          mem[1],
          swap[0],
          swap[1],
          disk[0],
          disk[1],
          net[0],
          net[1],
          uptime,
          System.currentTimeMillis());
    } catch (IOException | NumberFormatException e) {
      log.warn("本地指标采集失败: {}", e.getMessage());
      throw new IllegalStateException("指标采集失败", e);
    }
  }

  /** 上次 CPU 读数（idle, total），用于无阻塞增量计算；首次采样前为 null。 */
  private long[] lastCpuStat;

  /** 增量采样 CPU 使用率（千分比 0-1000，除以 10 得百分比）：基于上次采样读数做差值， 返回两次采样间隔内的平均使用率；首次采样无基线返回 0。无 sleep、不阻塞。 */
  private long[] readCpu() {
    long[] current = readCpuStat();
    long[] result = {0L, 0L};
    if (lastCpuStat != null) {
      long idleDelta = current[0] - lastCpuStat[0];
      long totalDelta = current[1] - lastCpuStat[1];
      long cpu =
          totalDelta > 0 && idleDelta >= 0 ? (totalDelta - idleDelta) * 1000 / totalDelta : 0;
      result[0] = cpu;
    }
    lastCpuStat = current;
    return result;
  }

  /** 读取 /proc/stat 第一行，返回 {idle+iowait, total}。 */
  private long[] readCpuStat() {
    try {
      String line = Files.readAllLines(Path.of("/proc/stat")).get(0);
      String[] parts = line.trim().split("\\s+");
      long total = 0;
      for (int i = 1; i < parts.length; i++) {
        total += Long.parseLong(parts[i]);
      }
      long idle = Long.parseLong(parts[4]) + Long.parseLong(parts[5]);
      return new long[] {idle, total};
    } catch (IOException | IndexOutOfBoundsException | NumberFormatException e) {
      log.warn("读取 /proc/stat 失败: {}", e.getMessage());
      return new long[] {0L, 0L};
    }
  }

  private long[] readMemInfo() throws IOException {
    long total = 0;
    long avail = 0;
    for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
      if (line.startsWith("MemTotal:")) {
        total = parseKb(line);
      } else if (line.startsWith("MemAvailable:")) {
        avail = parseKb(line);
      }
    }
    return new long[] {total, avail};
  }

  private long[] readSwap() throws IOException {
    long total = 0;
    long free = 0;
    for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
      if (line.startsWith("SwapTotal:")) {
        total = parseKb(line);
      } else if (line.startsWith("SwapFree:")) {
        free = parseKb(line);
      }
    }
    return new long[] {total, free};
  }

  private long[] readDisk() throws IOException {
    // 只统计存储根所在文件系统（重点 /home/wb/nas）
    var fs = new java.io.File(storageRoot);
    long total = fs.getTotalSpace() / 1024;
    long avail = fs.getUsableSpace() / 1024;
    return new long[] {total, avail};
  }

  private long[] readNet() throws IOException {
    long rx = 0;
    long tx = 0;
    for (String line : Files.readAllLines(Path.of("/proc/net/dev"))) {
      if (line.contains("eth") || line.contains("ens") || line.contains("enp")) {
        String[] parts = line.trim().split("\\s+");
        rx += Long.parseLong(parts[1]);
        tx += Long.parseLong(parts[9]);
      }
    }
    return new long[] {rx, tx};
  }

  private String[] readLoad() throws IOException {
    return Files.readString(Path.of("/proc/loadavg")).trim().split("\\s+");
  }

  private long readUptime() throws IOException {
    return (long)
        Double.parseDouble(Files.readString(Path.of("/proc/uptime")).trim().split("\\s+")[0]);
  }

  private long parseKb(String line) {
    String[] parts = line.trim().split("\\s+");
    return Long.parseLong(parts[1]);
  }
}
