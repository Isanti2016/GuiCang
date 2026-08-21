package com.guicang.nas.infra.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Helper PAM 校验器解析测试（不依赖 sudo，直接测 parseOutput 与 execHelper 降级）。 */
class HelperPAMVerifierTest {

  private final HelperPAMVerifier verifier = new HelperPAMVerifier();

  @Test
  void 解析成功输出() {
    PAMVerifyResult result =
        verifier.parseOutput(
            "{\"ok\":true,\"uid\":1001,\"gid\":2000,\"home\":\"/home/alice\",\"shell\":\"/usr/sbin/nologin\"}",
            "alice");
    assertThat(result.ok()).isTrue();
    assertThat(result.uid()).isEqualTo(1001L);
    assertThat(result.home()).isEqualTo("/home/alice");
  }

  @Test
  void 透传失败原因() {
    PAMVerifyResult result = verifier.parseOutput("{\"ok\":false,\"error\":\"用户名或密码错误\"}", "alice");
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isEqualTo("用户名或密码错误");
  }

  @Test
  void 缺少error字段用默认值() {
    PAMVerifyResult result = verifier.parseOutput("{\"ok\":false}", "alice");
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isEqualTo("用户名或密码错误");
  }

  @Test
  void 空输出解析失败按认证服务异常() {
    PAMVerifyResult result = verifier.parseOutput("", "alice");
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isEqualTo("认证服务异常");
  }

  @Test
  void helper不可执行时降级() throws Exception {
    java.lang.reflect.Field path = HelperPAMVerifier.class.getDeclaredField("helperPath");
    path.setAccessible(true);
    path.set(verifier, "/nonexistent/guicang-helper");
    PAMVerifyResult result = verifier.verify("alice", "password");
    // helper 调用失败 → 输出空 → 认证服务异常；无论如何非成功
    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isNotBlank();
  }
}
