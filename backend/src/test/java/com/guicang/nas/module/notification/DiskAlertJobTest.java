package com.guicang.nas.module.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.setting.SysSettingService;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** 磁盘告警任务测试：阈值跳过、越阈告警、24h 去重、异常静默。 */
class DiskAlertJobTest {

  private StorageService storageService;
  private NotificationService notificationService;
  private SysSettingService sysSettingService;
  private DiskAlertJob job;

  @BeforeEach
  void setUp() {
    storageService = mock(StorageService.class);
    notificationService = mock(NotificationService.class);
    sysSettingService = mock(SysSettingService.class);
    job = new DiskAlertJob(storageService, notificationService, sysSettingService);
  }

  @Test
  void thresholdDisabledSkips() {
    when(sysSettingService.getInt(Mockito.any())).thenReturn(0);
    job.checkDisk();
    verify(notificationService, never()).create(Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.any());
  }

  @Test
  void usageBelowThresholdNoAlert() throws Exception {
    when(sysSettingService.getInt(Mockito.any())).thenReturn(80);
    Path root = Path.of("/tmp/nas");
    when(storageService.root()).thenReturn(root);
    FileStore store = mock(FileStore.class);
    when(store.getTotalSpace()).thenReturn(100L);
    when(store.getUsableSpace()).thenReturn(30L); // 使用率 70% < 80
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.getFileStore(root)).thenReturn(store);
      job.checkDisk();
    }
    verify(notificationService, never()).create(Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.any());
  }

  @Test
  void usageOverThresholdCreatesAlert() throws Exception {
    when(sysSettingService.getInt(Mockito.any())).thenReturn(80);
    Path root = Path.of("/tmp/nas");
    when(storageService.root()).thenReturn(root);
    FileStore store = mock(FileStore.class);
    when(store.getTotalSpace()).thenReturn(100L);
    when(store.getUsableSpace()).thenReturn(10L); // 使用率 90% >= 80
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.getFileStore(root)).thenReturn(store);
      job.checkDisk();
    }
    verify(notificationService, times(1)).create(
        Mockito.eq("disk"), Mockito.anyString(), Mockito.anyString(), Mockito.isNull());
  }

  @Test
  void recentAlertDeduplicated() throws Exception {
    when(sysSettingService.getInt(Mockito.any())).thenReturn(80);
    Path root = Path.of("/tmp/nas");
    when(storageService.root()).thenReturn(root);
    when(notificationService.hasRecent("disk", 24L * 3600 * 1000)).thenReturn(true);
    FileStore store = mock(FileStore.class);
    when(store.getTotalSpace()).thenReturn(100L);
    when(store.getUsableSpace()).thenReturn(10L);
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.getFileStore(root)).thenReturn(store);
      job.checkDisk();
    }
    verify(notificationService, never()).create(Mockito.anyString(), Mockito.anyString(),
        Mockito.anyString(), Mockito.any());
  }

  @Test
  void exceptionSwallowed() throws Exception {
    when(sysSettingService.getInt(Mockito.any())).thenReturn(80);
    Path root = Path.of("/tmp/nas");
    when(storageService.root()).thenReturn(root);
    try (MockedStatic<Files> files = mockStatic(Files.class)) {
      files.when(() -> Files.getFileStore(root)).thenThrow(new RuntimeException("io"));
      assertDoesNotThrow(() -> job.checkDisk());
    }
  }
}
