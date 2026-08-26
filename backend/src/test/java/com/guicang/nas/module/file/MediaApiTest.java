package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 相册媒体接口测试：媒体收集应排除回收站内已删除内容。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaApiTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SysUserMapper sysUserMapper;
  @MockBean private PAMVerifier pamVerifier;

  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    Files.createDirectories(tempRoot.resolve("media/photos"));
    Files.writeString(tempRoot.resolve("media/photos/photo.jpg"), "jpg");
    insertUser("admin", 1L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
  }

  @Test
  void 删除后媒体列表不再包含回收站内容() throws Exception {
    // 删除图片 → 进回收站
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "media/photos/photo.jpg"))
        .andExpect(status().isOk());

    // media 列表不应包含 .guicang-trash 下的内容
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/files/media").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(body).doesNotContain(".guicang-trash");
    assertThat(body).doesNotContain("photo.jpg");
  }

  private void insertUser(String username, Long roleId, int enabled) {
    sysUserMapper.delete(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username));
    SysUser user = new SysUser();
    user.setUsername(username);
    user.setDisplayName(username);
    user.setEnabled(enabled);
    user.setRoleId(roleId);
    user.setHomePath("/home/" + username);
    user.setCreatedAt("2026-01-01T00:00:00");
    user.setUpdatedAt("2026-01-01T00:00:00");
    sysUserMapper.insert(user);
  }

  private String login(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/auth/login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new com.guicang.nas.module.auth.dto.LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
        .path("data")
        .path("token")
        .asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
