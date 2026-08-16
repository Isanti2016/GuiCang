package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 文件索引与搜索测试：操作后索引维护、按名命中、越权结果过滤。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileSearchTest {

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
    Files.createDirectories(tempRoot.resolve("personal/admin"));
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
  void 上传后按名搜索命中() throws Exception {
    upload("family_photo.png", adminToken);
    upload("meeting_notes.md", adminToken);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/search")
                    .param("q", "photo")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    assertThat(data.toString()).contains("family_photo.png").doesNotContain("meeting_notes");
  }

  @Test
  void 重命名后索引路径更新() throws Exception {
    upload("old_name.txt", adminToken);
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/files/rename")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/old_name.txt\",\"newName\":\"new_name.txt\"}"))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/search")
                    .param("q", "new_name")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).contains("new_name.txt");
  }

  @Test
  void guest搜索仅见有权限结果() throws Exception {
    upload("shared_doc.txt", adminToken);
    upload("admin_private.txt", adminToken); // personal/admin/ 下

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/search")
                    .param("q", ".txt")
                    .header("Authorization", bearer(guestToken)))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("shared_doc").doesNotContain("admin_private");
  }

  @Test
  void 删除后索引移除() throws Exception {
    upload("temp_file.md", adminToken);
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/temp_file.md"))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/search")
                    .param("q", "temp_file")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("temp_file");
  }

  private void upload(String filename, String token) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", filename, "text/plain", "content".getBytes(StandardCharsets.UTF_8));
    String dir = filename.startsWith("admin_") ? "personal/admin" : "shared";
    mockMvc
        .perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .param("path", dir)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk());
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
