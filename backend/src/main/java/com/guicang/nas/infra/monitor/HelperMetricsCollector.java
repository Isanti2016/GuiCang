package com.guicang.nas.infra.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * helper 指标采集（生产）：sudo 调宿主 guicang-helper metrics。
 *
 * <p>配置 guicang.metrics.source=helper（容器部署时必须，容器内 /proc 非宿主）。
 */
@Component
@ConditionalOnProperty(name = "guicang.metrics.source", havingValue = "helper")
public class HelperMetricsCollector implements MetricsCollector {

  private static final Logger log = LoggerFactory.getLogger(HelperMetricsCollector.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${guicang.helper.path:/usr/local/bin/guicang-helper}")
  private String helperPath;

  @Override
  public HostMetrics collect() {
    try {
      Process process = new ProcessBuilder("sudo", "-n", helperPath, "metrics").start();
      String output = new String(process.getInputStream().readAllBytes());
      if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS) || process.exitValue() != 0) {
        process.destroyForcibly();
        return fallback();
      }
      JsonNode node = objectMapper.readTree(output);
      if (!node.path("ok").asBoolean(false)) {
        return fallback();
      }
      return new HostMetrics(
          node.path("cpu").asDouble(),
          node.path("load1").asDouble(),
          node.path("load5").asDouble(),
          node.path("load15").asDouble(),
          node.path("mem_total_kb").asLong(),
          node.path("mem_avail_kb").asLong(),
          node.path("swap_total_kb").asLong(),
          node.path("swap_free_kb").asLong(),
          node.path("disk_total_kb").asLong(),
          node.path("disk_avail_kb").asLong(),
          node.path("net_rx_bytes").asLong(),
          node.path("net_tx_bytes").asLong(),
          node.path("uptime_sec").asLong(),
          System.currentTimeMillis());
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("helper 指标采集失败: {}", e.getMessage());
      return fallback();
    }
  }

  private HostMetrics fallback() {
    return new HostMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
  }
}
