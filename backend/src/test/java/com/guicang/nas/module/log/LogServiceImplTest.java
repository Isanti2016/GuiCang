package com.guicang.nas.module.log;

import static org.assertj.core.api.Assertions.assertThat;

import com.guicang.nas.module.log.dto.LogPage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/** 日志查看服务单测：解析 JSON、级别/关键字过滤、倒序分页。 */
class LogServiceImplTest {

  @TempDir Path tempDir;

  private LogServiceImpl service;

  @BeforeEach
  void setUp() throws Exception {
    service = new LogServiceImpl();
    ReflectionTestUtils.setField(service, "logDir", tempDir.toString());
    Files.writeString(
        tempDir.resolve("app.log"),
        """
        {"@timestamp":"2026-08-24T10:00:01.000Z","level":"INFO","logger_name":"com.guicang.nas.A","thread_name":"main","message":"startup ok"}
        {"@timestamp":"2026-08-24T10:00:02.000Z","level":"ERROR","logger_name":"com.guicang.nas.B","thread_name":"http-1","message":"upload failed"}
        {"@timestamp":"2026-08-24T10:00:03.000Z","level":"WARN","logger_name":"com.guicang.nas.C","thread_name":"http-2","message":"slow query detected"}
        """);
  }

  @Test
  void 解析全部并按时间倒序() {
    LogPage page = service.query(null, null, 1, 10);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records().get(0).message()).isEqualTo("slow query detected");
    assertThat(page.records().get(2).message()).isEqualTo("startup ok");
  }

  @Test
  void 按级别过滤() {
    LogPage page = service.query("ERROR", null, 1, 10);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.records().get(0).level()).isEqualTo("ERROR");
    assertThat(page.records().get(0).message()).isEqualTo("upload failed");
  }

  @Test
  void 按关键字过滤() {
    LogPage page = service.query(null, "upload", 1, 10);
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.records().get(0).logger()).isEqualTo("com.guicang.nas.B");
  }

  @Test
  void 分页() {
    LogPage page = service.query(null, null, 2, 2);
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.records()).hasSize(1);
    assertThat(page.records().get(0).message()).isEqualTo("startup ok");
  }
}
