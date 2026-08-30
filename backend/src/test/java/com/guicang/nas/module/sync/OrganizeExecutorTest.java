package com.guicang.nas.module.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 自动整理执行器测试：日期/类型规则、move/copy、冲突策略、索引同步、空目录清理。 */
@SpringBootTest
@ActiveProfiles("test")
class OrganizeExecutorTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
  }

  @Autowired private OrganizeExecutor executor;

  @Autowired private FileIndexMapper fileIndexMapper;

  @BeforeEach
  void cleanIndex() {
    fileIndexMapper.delete(null);
  }

  private SyncTask task(
      String type, String source, String target, String rule, String action, String conflict) {
    SyncTask t = new SyncTask();
    t.setTaskType(type);
    t.setSourceConfig(source);
    t.setTargetConfig(target);
    t.setRuleType(rule);
    t.setAction(action);
    t.setConflict(conflict);
    return t;
  }

  @Test
  void 按日期移动并同步索引() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox"));
    Files.writeString(tempRoot.resolve("inbox/photo.jpg"), "x");
    // 建立源索引（模拟此前已索引）
    fileIndexMapper.insert(index("inbox/photo.jpg", "photo.jpg", "image"));

    SyncTask t = task("organize", "inbox", "photos", "date_month", "move", "rename");
    OrganizeResult result = executor.execute(t, new DateOrganizeRule("date_month"));

    assertThat(result.succeeded()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    // 文件已移动
    assertThat(Files.exists(tempRoot.resolve("inbox/photo.jpg"))).isFalse();
    String sub =
        new DateOrganizeRule("date_month")
            .subPath(tempRoot.resolve("inbox/photo.jpg"), "photo.jpg");
    // 目标存在（子目录按当前年月）
    assertThat(Files.exists(tempRoot.resolve("photos").resolve(sub).resolve("photo.jpg"))).isTrue();
    // 索引已迁移
    assertThat(
            fileIndexMapper.selectList(null).stream()
                .anyMatch(i -> i.getPath().startsWith("photos/")))
        .isTrue();
    assertThat(
            fileIndexMapper.selectList(null).stream()
                .noneMatch(i -> i.getPath().equals("inbox/photo.jpg")))
        .isTrue();
  }

  @Test
  void 按类型复制保留源文件() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox"));
    Files.writeString(tempRoot.resolve("inbox/a.jpg"), "x");
    Files.writeString(tempRoot.resolve("inbox/b.md"), "y");

    SyncTask t = task("organize", "inbox", "lib", "kind", "copy", "rename");
    OrganizeResult result = executor.execute(t, new KindOrganizeRule());

    assertThat(result.succeeded()).isEqualTo(2);
    // copy 后源仍在
    assertThat(Files.exists(tempRoot.resolve("inbox/a.jpg"))).isTrue();
    assertThat(Files.exists(tempRoot.resolve("lib/image/a.jpg"))).isTrue();
    assertThat(Files.exists(tempRoot.resolve("lib/note/b.md"))).isTrue();
    // 索引同步（copy → upsert 新路径，源索引仍在）
    assertThat(
            fileIndexMapper.selectList(null).stream()
                .anyMatch(i -> i.getPath().equals("lib/image/a.jpg")))
        .isTrue();
  }

  @Test
  void 冲突重命名与跳过() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox"));
    Files.createDirectories(tempRoot.resolve("out"));
    Files.writeString(tempRoot.resolve("inbox/a.jpg"), "new");
    Files.writeString(tempRoot.resolve("out/a.jpg"), "old");

    // rename：目标已存在 → 生成 a (1).jpg
    SyncTask t1 = task("organize", "inbox", "out", "kind", "move", "rename");
    OrganizeResult r1 = executor.execute(t1, new KindOrganizeRule());
    assertThat(r1.succeeded()).isEqualTo(1);
    assertThat(Files.exists(tempRoot.resolve("out/image/a (1).jpg"))).isTrue();

    // skip：源重新放一个同名 → 不处理，计 skipped
    Files.writeString(tempRoot.resolve("inbox/a.jpg"), "new2");
    SyncTask t2 = task("organize", "inbox", "out", "kind", "move", "skip");
    OrganizeResult r2 = executor.execute(t2, new KindOrganizeRule());
    assertThat(r2.succeeded()).isZero();
    assertThat(r2.skipped()).isEqualTo(1);
    assertThat(Files.exists(tempRoot.resolve("inbox/a.jpg"))).isTrue();
  }

  @Test
  void overwrite冲突目标已有索引不报错() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox"));
    Files.createDirectories(tempRoot.resolve("out/image"));
    Files.writeString(tempRoot.resolve("inbox/a.jpg"), "new");
    Files.writeString(tempRoot.resolve("out/image/a.jpg"), "old");
    // 源与目标都已建索引（模拟此前已索引）
    fileIndexMapper.insert(index("inbox/a.jpg", "a.jpg", "image"));
    fileIndexMapper.insert(index("out/image/a.jpg", "a.jpg", "image"));

    SyncTask t = task("organize", "inbox", "out", "kind", "move", "overwrite");
    OrganizeResult result = executor.execute(t, new KindOrganizeRule());

    assertThat(result.succeeded()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    // 目标文件被覆盖，索引只有一份（新路径）
    assertThat(Files.readString(tempRoot.resolve("out/image/a.jpg"))).isEqualTo("new");
    assertThat(
            fileIndexMapper.selectList(null).stream()
                .filter(i -> i.getPath().endsWith("a.jpg"))
                .count())
        .isEqualTo(1);
    assertThat(Files.exists(tempRoot.resolve("inbox/a.jpg"))).isFalse();
  }

  @Test
  void 空目录被清理但源根保留() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox/sub/deep"));
    Files.writeString(tempRoot.resolve("inbox/sub/deep/f.txt"), "x");

    SyncTask t = task("organize", "inbox", "out", "kind", "move", "rename");
    executor.execute(t, new KindOrganizeRule());

    assertThat(Files.exists(tempRoot.resolve("inbox/sub/deep"))).isFalse();
    assertThat(Files.exists(tempRoot.resolve("inbox/sub"))).isFalse();
    assertThat(Files.exists(tempRoot.resolve("inbox"))).isTrue();
    assertThat(Files.exists(tempRoot.resolve("out/note/f.txt"))).isTrue();
  }

  private FileIndex index(String path, String name, String kind) {
    FileIndex i = new FileIndex();
    i.setPath(path);
    i.setName(name);
    i.setKind(kind);
    i.setSize(1L);
    i.setMtime(System.currentTimeMillis());
    i.setIndexedAt(System.currentTimeMillis());
    return i;
  }
}
