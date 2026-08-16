package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.guicang.nas.common.BizException;
import com.guicang.nas.module.user.SysUserDir;
import com.guicang.nas.module.user.SysUserDirMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 目录级权限判定测试：admin 全权限、personal/shared/media 角色规则、sys_user_dir 附加授权。 */
@SpringBootTest
@ActiveProfiles("test")
class DirPermissionTest {

  private static final Path TEMP_ROOT = Path.of("/tmp/guicang-dirperm-test");

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> TEMP_ROOT.toString());
  }

  @Autowired private DirPermissionService dirPermissionService;

  @Autowired private SysUserDirMapper sysUserDirMapper;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(TEMP_ROOT);
  }

  @BeforeEach
  void cleanGrants() {
    sysUserDirMapper.delete(null);
  }

  @Test
  void admin任意路径通过() {
    dirPermissionService.check("admin", List.of("ROLE_ADMIN"), "backups/secret", DirPerm.MANAGE);
    assertThatCode(
            () ->
                dirPermissionService.check(
                    "admin", List.of("ROLE_ADMIN"), "backups/secret", DirPerm.MANAGE))
        .doesNotThrowAnyException();
  }

  @Test
  void member可访问本人个人目录() {
    dirPermissionService.check("bob", List.of("ROLE_MEMBER"), "personal/bob/docs", DirPerm.WRITE);
  }

  @Test
  void member不能访问他人个人目录() {
    assertThatThrownBy(
            () ->
                dirPermissionService.check(
                    "bob", List.of("ROLE_MEMBER"), "personal/alice/secret", DirPerm.READ))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("无权限");
  }

  @Test
  void member可写shared() {
    dirPermissionService.check("bob", List.of("ROLE_MEMBER"), "shared/team", DirPerm.WRITE);
  }

  @Test
  void guest只能读shared() {
    assertThatCode(
            () ->
                dirPermissionService.check(
                    "carol", List.of("ROLE_GUEST"), "shared/team", DirPerm.READ))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                dirPermissionService.check(
                    "carol", List.of("ROLE_GUEST"), "shared/team", DirPerm.WRITE))
        .isInstanceOf(BizException.class);
  }

  @Test
  void 登录用户可读media() {
    dirPermissionService.check("carol", List.of("ROLE_GUEST"), "media/photos/2026", DirPerm.READ);
  }

  @Test
  void sysUserDir附加授权放开只读目录() {
    SysUserDir grant = new SysUserDir();
    grant.setUsername("bob");
    grant.setPath("media/photos");
    grant.setPerm("write");
    sysUserDirMapper.insert(grant);

    dirPermissionService.check("bob", List.of("ROLE_MEMBER"), "media/photos/new", DirPerm.WRITE);
  }

  @Test
  void 无授权路径被拒() {
    assertThatThrownBy(
            () ->
                dirPermissionService.check(
                    "carol", List.of("ROLE_GUEST"), "backups/daily", DirPerm.READ))
        .isInstanceOf(BizException.class);
  }
}
