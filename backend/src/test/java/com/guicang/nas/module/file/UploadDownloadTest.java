package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 上传下载测试：流式上传、危险扩展名拦截、Range 206、越权拒绝（临时存储根）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadDownloadTest {

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
    Files.createDirectories(tempRoot.resolve("personal/bob"));
  }

  @BeforeEach
  void setUp() throws Exception {
    insertUser("admin", 1L, 1);
    insertUser("carol", 3L, 1); // guest
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    when(pamVerifier.verify("carol", "Carol-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1004, 2000, "/home/carol", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
    guestToken = login("carol", "Carol-Pass-2026!");
  }

  @Test
  void 上传文件落盘并返回条目() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "hello.md", "text/markdown", "# 你好\n内容".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .param("path", "shared")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.name").value("hello.md"))
        .andExpect(jsonPath("$.data.kind").value("note"));

    assertThat(Files.readString(tempRoot.resolve("shared/hello.md"))).contains("你好");
    // 临时目录应无残留
    try (var tmp = Files.list(tempRoot.resolve(".guicang-tmp"))) {
      assertThat(tmp).isEmpty();
    }
  }

  @Test
  void 危险扩展名被拦截() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "evil.sh", "application/octet-stream", "#!/bin/bash\n".getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .param("path", "shared")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("不允许上传该类型文件: evil.sh"));
  }

  @Test
  void guest不能上传到shared() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .param("path", "shared")
                .header("Authorization", bearer(guestToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("无权限执行该操作"));
  }

  @Test
  void 下载无Range返回200完整内容() throws Exception {
    Files.writeString(tempRoot.resolve("shared/readme.txt"), "0123456789");

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/download")
                    .param("path", "shared/readme.txt")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(
                header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("readme.txt")))
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).isEqualTo("0123456789");
  }

  @Test
  void 下载带Range返回206部分内容() throws Exception {
    Files.writeString(tempRoot.resolve("shared/video.mp4"), "0123456789abcdef");

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/stream")
                    .param("path", "shared/video.mp4")
                    .header("Authorization", bearer(adminToken))
                    .header(HttpHeaders.RANGE, "bytes=0-3"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/16"))
            .andReturn();

    assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("0123".getBytes());
  }

  @Test
  void 越权下载被拒() throws Exception {
    Files.createDirectories(tempRoot.resolve("personal/admin"));
    Files.writeString(tempRoot.resolve("personal/admin/secret.txt"), "x");
    mockMvc
        .perform(
            get("/api/v1/files/stream")
                .param("path", "personal/admin/secret.txt")
                .header("Authorization", bearer(guestToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 未登录访问下载被拒401() throws Exception {
    mockMvc
        .perform(get("/api/v1/files/stream").param("path", "shared/readme.txt"))
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
