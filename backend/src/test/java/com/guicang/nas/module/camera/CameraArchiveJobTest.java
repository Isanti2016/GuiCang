package com.guicang.nas.module.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** CameraArchiveJob 日期解析 / 摄像头名清洗 / 视频格式判定逻辑测试。 */
class CameraArchiveJobTest {

  @Test
  void dateFromName() {
    assertEquals("2026-08-31", CameraArchiveJob.dateOf("CAM01_20260831_120000.mp4", 0L));
  }

  @Test
  void dateFromNameWithPrefix() {
    assertEquals("2026-01-02", CameraArchiveJob.dateOf("backyard_20260102_000000.mp4", 0L));
  }

  @Test
  void dateFallbackToMtime() {
    long mtime =
        LocalDate.of(2026, 7, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    assertEquals("2026-07-15", CameraArchiveJob.dateOf("recording.mp4", mtime));
  }

  @Test
  void invalidDateFallsBackToMtime() {
    long mtime =
        LocalDate.of(2026, 7, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    assertEquals("2026-07-15", CameraArchiveJob.dateOf("x_20269999_000.mp4", mtime));
  }

  @Test
  void sanitizeKeepsChinese() {
    assertEquals("客厅", CameraArchiveJob.sanitize(" 客厅 "));
  }

  @Test
  void sanitizeBlocksTraversal() {
    assertEquals("default", CameraArchiveJob.sanitize(".."));
    assertEquals("a_b", CameraArchiveJob.sanitize("a/b"));
    assertEquals("a_b", CameraArchiveJob.sanitize("a.b"));
    assertEquals("a_b", CameraArchiveJob.sanitize("a..b"));
  }

  @Test
  void sanitizeEmptyFallsBack() {
    assertEquals("default", CameraArchiveJob.sanitize(""));
    assertEquals("default", CameraArchiveJob.sanitize(".."));
  }

  @Test
  void videoExtCaseInsensitive() {
    assertTrue(CameraArchiveJob.isVideoExt("MP4"));
    assertTrue(CameraArchiveJob.isVideoExt("mkv"));
    assertFalse(CameraArchiveJob.isVideoExt("jpg"));
    assertFalse(CameraArchiveJob.isVideoExt(null));
  }
}
