package com.guicang.nas.infra.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Helper PAM 校验器解析测试：用临时假 helper 脚本模拟输出，验证 JSON 解析路径。 */
class HelperPAMVerifierTest {

  @TempDir Path tempDir;

  private HelperPAMVerifier verifierWith(Path script, String body) throws Exception {
    Path helper = tempDir.resolve("fake-helper");
    Files.writeString(helper, "#!/bin/sh\nprintf '%s' '" + body + "'\n");
    helper.toFile().setExecutable(true);
    HelperPAMVerifier verifier = new HelperPAMVerifier();
    Field path = HelperPAMVerifier.class.getDeclaredField("helperPath");
    path.setAccessible(true);
    path.set(verifier, helper.toString());
    return verifier;
  }

  @Test
  void 解析成功输出() throws Exception {
    HelperPAMVerifier verifier =
        verifierWith(
            tempDir.resolve("fake-helper"),
            "{\"ok\":true,\"uid\":1001,\"gid\":2000,\"home\":\"/home/alice\",\"shell\":\"/usr/sbin/nologin\"}");
    PAMVerifyResult result = verifier.verify("alice", "password");
    assertThat(result.ok()).isTrue();
    assertThat(result.uid()).isEqualTo(1001L);
    assertThat(result.home()).isEqualTo("/home/alice");
  }

  @Test
  void 透传失败原因() throws Exception {
    HelperPAMVerifier verifier =
        verifierWith(tempDir.resolve("fake-helper"), "{\"ok\":false,\"error\":\"用户名或密码错误\"}");
    PAMVerifyResult result = verifier.verify("alice", "wrong");
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isEqualTo("用户名或密码错误");
  }

  @Test
  void helper不可执行时优雅降级() throws Exception {
    Path helper = tempDir.resolve("no-exec");
    Files.writeString(helper, "not a script");
    // 不可执行：sudo 会拒绝，输出为空 → 解析失败 → 认证服务异常/未就绪（非成功即可）
    HelperPAMVerifier verifier = new HelperPAMVerifier();
    Field path = HelperPAMVerifier.class.getDeclaredField("helperPath");
    path.setAccessible(true);
    path.set(verifier, helper.toString());
    PAMVerifyResult result = verifier.verify("alice", "password");
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isNotBlank();
  }
}
