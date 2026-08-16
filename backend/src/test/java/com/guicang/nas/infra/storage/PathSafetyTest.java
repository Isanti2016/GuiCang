package com.guicang.nas.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guicang.nas.common.BizException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 路径规范化与越权校验测试（防目录穿越/绝对路径逃逸）。 */
class PathSafetyTest {

  private final Path root = Path.of("/tmp/guicang-path-test-root");

  @Test
  void 正常相对路径解析到根内() {
    Path resolved = PathUtils.resolve(root, "shared/photo.jpg");
    assertThat(resolved).isEqualTo(root.resolve("shared/photo.jpg").toAbsolutePath().normalize());
  }

  @Test
  void 空路径视为根目录() {
    assertThat(PathUtils.resolve(root, "  ")).isEqualTo(root.toAbsolutePath().normalize());
  }

  @Test
  void 绝对路径被拒绝() {
    assertThatThrownBy(() -> PathUtils.resolve(root, "/etc/passwd"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("仅支持相对路径");
  }

  @Test
  void 目录穿越被拒绝() {
    assertThatThrownBy(() -> PathUtils.resolve(root, "../../etc/passwd"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("..");
    assertThatThrownBy(() -> PathUtils.resolve(root, "shared/../../secret"))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("..");
  }

  @Test
  void 路径越界被拒绝() {
    assertThatThrownBy(() -> PathUtils.resolve(root, "..")).isInstanceOf(BizException.class);
  }
}
