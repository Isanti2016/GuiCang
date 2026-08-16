package com.guicang.nas.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * SQLite 数据目录自动创建。
 *
 * <p>sqlite-jdbc 打开数据库文件时不会自动创建父目录，本类在应用启动前确保数据目录存在 （如 ${user.dir}/data 或容器内的 /data）。
 */
@Configuration
public class SqliteDataDirConfig {

  private static final Logger log = LoggerFactory.getLogger(SqliteDataDirConfig.class);

  @Value("${spring.datasource.url:}")
  private String datasourceUrl;

  @PostConstruct
  public void ensureDataDirExists() {
    if (!datasourceUrl.startsWith("jdbc:sqlite:file:")) {
      return;
    }
    String filePart = datasourceUrl.substring("jdbc:sqlite:file:".length());
    int queryIndex = filePart.indexOf('?');
    if (queryIndex >= 0) {
      filePart = filePart.substring(0, queryIndex);
    }
    if (filePart.isEmpty() || filePart.equals(":memory:") || filePart.contains("mode=memory")) {
      return;
    }
    Path parent = Path.of(filePart).toAbsolutePath().getParent();
    if (parent == null) {
      return;
    }
    try {
      Files.createDirectories(parent);
      log.info("SQLite 数据目录已就绪: {}", parent);
    } catch (IOException e) {
      throw new IllegalStateException("创建 SQLite 数据目录失败: " + parent, e);
    }
  }
}
