package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** md/txt 预览与编辑测试：读写、扩展名白名单、覆盖保存、权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileTextTest {

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

  private String guestToken;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("shared"));
  }

  @BeforeEach
  void setUp() throws Exception {
    insertUser("admin", 1L, 1);
    insertUser("carol", 3L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    when(pamVerifier.verify("carol", "Carol-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1004, 2000, "/home/carol", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
    guestToken = login("carol", "Carol-Pass-2026!");
  }

  @Test
  void 保存并读取md() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/files/write")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/note.md\",\"content\":\"# 标题\\n正文内容\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));

    assertThat(Files.readString(tempRoot.resolve("shared/note.md"))).contains("# 标题");

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/text")
                    .param("path", "shared/note.md")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            objectMapper
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .asText())
        .contains("正文内容");
  }

  @Test
  void 覆盖保存已存在文件() throws Exception {
    Files.writeString(tempRoot.resolve("shared/a.txt"), "old");
    mockMvc
        .perform(
            put("/api/v1/files/write")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/a.txt\",\"content\":\"new\"}"))
        .andExpect(status().isOk());
    assertThat(Files.readString(tempRoot.resolve("shared/a.txt"))).isEqualTo("new");
  }

  @Test
  void 非文本扩展名保存被拒() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/files/write")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/a.html\",\"content\":\"<html>\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("仅支持编辑 md/txt/markdown 文本文件"));
  }

  @Test
  void guest不能写shared() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/files/write")
                .header("Authorization", bearer(guestToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/a.md\",\"content\":\"x\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("无权限执行该操作"));
  }

  @Test
  void 读取不存在文件被拒() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/files/text")
                .param("path", "shared/nope.md")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 未登录访问被拒401() throws Exception {
    mockMvc
        .perform(get("/api/v1/files/text").param("path", "shared/a.md"))
        .andExpect(status().isUnauthorized());
  }

  private void insertUser(String username, Long roleId, int enabled) {
    sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    SysUser user = new SysUser();
    user.setUsername(username);
    user.setDisplayName(username);
    user.setEnabled(enabled);
    user.setRoleId(roleId);
    user.setHomePath("/home/" + username);
    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    sysUserMapper.insert(user);
  }

  private String login(String username, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new com.guicang.nas.module.auth.dto.LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .path("data")
        .path("token")
        .asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
