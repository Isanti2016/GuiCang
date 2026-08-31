package com.guicang.nas.module.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 认证链路测试：登录（PAM mock）、令牌访问、未认证/非法令牌拒绝。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @MockBean private PAMVerifier pamVerifier;

  @BeforeEach
  void setUp() {
    // 登录要求 sys_user 存在且启用（admin 角色）
    sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "alice"));
    SysUser user = new SysUser();
    user.setUsername("alice");
    user.setDisplayName("爱丽丝");
    user.setEnabled(1);
    user.setRoleId(1L);
    user.setHomePath("/home/alice");
    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    sysUserMapper.insert(user);
  }

  @Test
  void 登录成功返回令牌与用户信息() throws Exception {
    when(pamVerifier.verify("alice", "secret-pass"))
        .thenReturn(PAMVerifyResult.success(1005, 2000, "/home/alice", "/usr/sbin/nologin"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"secret-pass\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.token").isNotEmpty())
        .andExpect(jsonPath("$.data.user.username").value("alice"))
        .andExpect(jsonPath("$.data.user.uid").value(1005));
  }

  @Test
  void 密码错误返回401统一码() throws Exception {
    when(pamVerifier.verify(anyString(), anyString()))
        .thenReturn(PAMVerifyResult.failure("用户名或密码错误"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED))
        .andExpect(jsonPath("$.message").value("用户名或密码错误"));
  }

  @Test
  void 未带令牌访问受保护接口返回401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED));
  }

  @Test
  void 带令牌访问me返回用户信息() throws Exception {
    when(pamVerifier.verify("alice", "secret-pass"))
        .thenReturn(PAMVerifyResult.success(1005, 2000, "/home/alice", "/usr/sbin/nologin"));

    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"alice\",\"password\":\"secret-pass\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = objectMapper.readTree(body).path("data").path("token").asText();

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("alice"))
        .andExpect(jsonPath("$.data.uid").value(1005));
  }

  @Test
  void 非法令牌返回401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer bad.token.value"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED));
  }

  @Test
  void 登录参数校验失败返回400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ResultCodes.BAD_REQUEST));
  }

  @Test
  void TOTP恢复码可登录且一次性消费() throws Exception {
    when(pamVerifier.verify("alice", "secret-pass"))
        .thenReturn(PAMVerifyResult.success(1005, 2000, "/home/alice", "/usr/sbin/nologin"));

    // 1. 登录拿令牌
    String loginBody =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"alice\",\"password\":\"secret-pass\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = objectMapper.readTree(loginBody).path("data").path("token").asText();

    // 2. 开启两步验证，返回 10 个恢复码
    String enableBody =
        mockMvc
            .perform(post("/api/v1/auth/totp/enable").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.secret").isNotEmpty())
            .andExpect(jsonPath("$.data.recoveryCodes.length()").value(10))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String recoveryCode =
        objectMapper.readTree(enableBody).path("data").path("recoveryCodes").get(0).asText();

    // 3. 用恢复码登录成功（TOTP 已开启，动态码/恢复码任一通过）
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"alice\",\"password\":\"secret-pass\",\"totp\":\""
                        + recoveryCode
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));

    // 4. 同一恢复码二次使用被拒绝（一次性消费）
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"alice\",\"password\":\"secret-pass\",\"totp\":\""
                        + recoveryCode
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED))
        .andExpect(jsonPath("$.message").value("两步验证码错误"));
  }
}
