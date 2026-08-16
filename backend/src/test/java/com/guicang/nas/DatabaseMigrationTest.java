package com.guicang.nas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 验证 Flyway 迁移在 SQLite 上建出基础表且可读写。 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void flyway迁移创建基础表() {
    List<String> tables =
        jdbcTemplate.queryForList(
            "SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
    assertThat(tables).contains("sys_config", "audit_log");
  }

  @Test
  void sysConfig可读写() {
    jdbcTemplate.update(
        "INSERT INTO sys_config (key, value, updated_at) VALUES (?, ?, ?)",
        "site.name",
        "归藏",
        "2026-08-16 00:00:00");
    String value =
        jdbcTemplate.queryForObject(
            "SELECT value FROM sys_config WHERE key = ?", String.class, "site.name");
    assertThat(value).isEqualTo("归藏");
  }

  @Test
  void auditLog可插入() {
    jdbcTemplate.update(
        "INSERT INTO audit_log (username, action, result, created_at) VALUES (?, ?, ?, ?)",
        "admin",
        "login",
        "success",
        System.currentTimeMillis());
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
    assertThat(count).isEqualTo(1);
  }
}
