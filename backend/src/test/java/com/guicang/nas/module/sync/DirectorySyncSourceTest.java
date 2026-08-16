package com.guicang.nas.module.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.guicang.nas.infra.storage.FileTypeUtils;
import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 目录扫描同步测试：新增/变更/删除增量识别（临时存储根）。 */
@SpringBootTest
@ActiveProfiles("test")
class DirectorySyncSourceTest {

  private static final Path TEMP_ROOT = Path.of("/tmp/guicang-sync-test");

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> TEMP_ROOT.toString());
  }

  @Autowired private SyncSource syncSource;

  @Autowired private FileIndexMapper fileIndexMapper;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(TEMP_ROOT.resolve("media/photos"));
    Files.writeString(TEMP_ROOT.resolve("media/photos/a.jpg"), "a");
    Files.writeString(TEMP_ROOT.resolve("media/photos/b.md"), "old-content");
    Files.writeString(TEMP_ROOT.resolve("media/photos/c.txt"), "c");
  }

  @BeforeEach
  void cleanIndex() {
    fileIndexMapper.delete(null);
  }

  @Test
  void 首次扫描全部视为新增() {
    ChangeSet changeSet = syncSource.scan("media/photos");
    assertThat(changeSet.added())
        .extracting(FileIndex::getName)
        .containsExactlyInAnyOrder("a.jpg", "b.md", "c.txt");
    assertThat(changeSet.updated()).isEmpty();
    assertThat(changeSet.deleted()).isEmpty();
  }

  @Test
  void 变更与删除被识别() throws Exception {
    // 预置索引：a.jpg 与磁盘一致（不变更），b.md 内容已变（mtime 更新），stale.txt 磁盘已删
    Path aPath = TEMP_ROOT.resolve("media/photos/a.jpg");
    insertIndex(
        "media/photos/a.jpg", Files.size(aPath), Files.getLastModifiedTime(aPath).toMillis());
    insertIndex("media/photos/b.md", 99L, 99L);
    insertIndex("media/photos/stale.txt", 1L, 1L);

    Files.setLastModifiedTime(
        TEMP_ROOT.resolve("media/photos/b.md"), FileTime.fromMillis(System.currentTimeMillis()));

    ChangeSet changeSet = syncSource.scan("media/photos");

    assertThat(changeSet.added()).extracting(FileIndex::getName).containsExactly("c.txt");
    assertThat(changeSet.updated()).extracting(FileIndex::getName).containsExactly("b.md");
    assertThat(changeSet.deleted()).containsExactly("media/photos/stale.txt");
  }

  @Test
  void 应用增量后重复扫描无变化() throws Exception {
    // 扫描为只读；应用增量（模拟 SyncService 行为）后再次扫描应无变化
    ChangeSet first = syncSource.scan("media/photos");
    first.added().forEach(idx -> insertIndex(idx.getPath(), idx.getSize(), idx.getMtime()));

    ChangeSet second = syncSource.scan("media/photos");
    assertThat(second.total()).isZero();
  }

  private void insertIndex(String path, long size, long mtime) {
    FileIndex index = new FileIndex();
    index.setPath(path);
    index.setName(path.substring(path.lastIndexOf('/') + 1));
    index.setKind(FileTypeUtils.kind(path.substring(path.lastIndexOf('/') + 1)));
    index.setSize(size);
    index.setMtime(mtime);
    index.setIndexedAt(System.currentTimeMillis());
    fileIndexMapper.insert(index);
  }
}
