package com.guicang.nas.module.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.module.log.dto.LogEntry;
import com.guicang.nas.module.log.dto.LogPage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 系统日志查看服务实现：读取 logback 输出的结构化 JSON 日志文件。 */
@Service
public class LogServiceImpl implements LogService {

  private static final Logger log = LoggerFactory.getLogger(LogServiceImpl.class);
  private static final Set<String> LEVELS = Set.of("DEBUG", "INFO", "WARN", "ERROR");
  private static final long MAX_LINES = 50_000;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${GUICANG_LOG_DIR:./data/logs}")
  private String logDir;

  @Override
  public LogPage query(String level, String keyword, long page, long size) {
    long safePage = Math.max(page, 1);
    long safeSize = Math.min(Math.max(size, 1), 200);

    List<LogEntry> all = readLogs();
    if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
      String upper = level.toUpperCase();
      all = all.stream().filter(e -> e.level().equalsIgnoreCase(upper)).toList();
    }
    if (keyword != null && !keyword.isBlank()) {
      String kw = keyword.toLowerCase();
      all =
          all.stream()
              .filter(
                  e ->
                      (e.message() != null && e.message().toLowerCase().contains(kw))
                          || (e.logger() != null && e.logger().toLowerCase().contains(kw)))
              .toList();
    }
    // 时间倒序：最新在前（过滤结果可能是不可变 List，先转可变）
    all = new ArrayList<>(all);
    all.sort(Comparator.comparingLong(LogEntry::timestamp).reversed());

    long total = all.size();
    int from = (int) ((safePage - 1) * safeSize);
    if (from >= all.size()) {
      return new LogPage(List.of(), total);
    }
    int to = (int) Math.min(from + safeSize, all.size());
    return new LogPage(new ArrayList<>(all.subList(from, to)), total);
  }

  @Override
  public List<String> levels() {
    return List.of("DEBUG", "INFO", "WARN", "ERROR");
  }

  /** 读取日志目录下最新写入的 JSON 日志文件，解析为条目列表（限 MAX_LINES 行，保留最新）。 */
  private List<LogEntry> readLogs() {
    Path dir = Path.of(logDir);
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    Path target = findLatestFile(dir);
    if (target == null) {
      return List.of();
    }
    List<LogEntry> entries = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        LogEntry entry = parseLine(line);
        if (entry != null) {
          entries.add(entry);
          if (entries.size() > MAX_LINES) {
            // 保留最新的 MAX_LINES 条（行序即时间序，取尾部）
            entries.remove(0);
          }
        }
      }
    } catch (IOException e) {
      log.warn("读取日志文件失败: {}", target, e);
      throw new BizException("读取日志失败");
    }
    return entries;
  }

  /** 找最新日志文件：app.log 优先，其次按修改时间最新的滚动文件。 */
  private Path findLatestFile(Path dir) {
    Path active = dir.resolve("app.log");
    if (Files.isRegularFile(active)) {
      return active;
    }
    try (var stream = Files.list(dir)) {
      return stream
          .filter(p -> p.getFileName().toString().endsWith(".json.log"))
          .filter(p -> Files.isRegularFile(p))
          .max(
              Comparator.comparingLong(
                  p -> {
                    try {
                      return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                      return 0L;
                    }
                  }))
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  /** 解析一行 logback JSON 日志，失败返回 null。 */
  private LogEntry parseLine(String line) {
    try {
      JsonNode node = objectMapper.readTree(line);
      String ts = node.path("@timestamp").asText("");
      long millis = parseTimestamp(ts);
      String level = node.path("level").asText("INFO");
      String logger = node.path("logger_name").asText("");
      String thread = node.path("thread_name").asText("");
      String message = node.path("message").asText("");
      return new LogEntry(millis, level, logger, thread, message);
    } catch (IOException e) {
      // 单行解析失败跳过（日志行可能被截断）
      return null;
    }
  }

  private long parseTimestamp(String ts) {
    if (ts == null || ts.isBlank()) {
      return 0L;
    }
    try {
      return Instant.parse(ts).toEpochMilli();
    } catch (DateTimeParseException e) {
      try {
        return Instant.parse(ts.replace(" ", "T")).toEpochMilli();
      } catch (DateTimeParseException e2) {
        return 0L;
      }
    }
  }
}
