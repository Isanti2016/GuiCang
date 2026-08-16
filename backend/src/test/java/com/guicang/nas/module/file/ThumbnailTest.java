package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
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

/** 图片缩略图测试：懒生成、JPEG 缓存、非图片拒绝（临时存储根与缩略图目录）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ThumbnailTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
    registry.add("guicang.thumbnails-dir", () -> tempRoot.resolve("thumbs").toString());
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @MockBean private PAMVerifier pamVerifier;

  private String adminToken;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("shared"));
  }

  @BeforeEach
  void setUp() throws Exception {
    insertUser("admin", 1L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
  }

  @Test
  void 图片生成JPEG缩略图并缓存() throws Exception {
    // 生成 200x150 真实 PNG
    BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
    image.getGraphics().fillRect(0, 0, 200, 150);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);

    mockMvc
        .perform(
            multipart("/api/v1/files/upload")
                .file(new MockMultipartFile("file", "photo.png", "image/png", baos.toByteArray()))
                .param("path", "shared")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/thumbnail")
                    .param("path", "shared/photo.png")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andReturn();

    byte[] thumbBytes = result.getResponse().getContentAsByteArray();
    assertThat(thumbBytes).isNotEmpty();
    // JPEG 魔数
    assertThat(thumbBytes[0]).isEqualTo((byte) 0xFF);
    assertThat(thumbBytes[1]).isEqualTo((byte) 0xD8);
    // 缩略图缓存已落盘
    try (var thumbs = Files.list(tempRoot.resolve("thumbs"))) {
      assertThat(thumbs).isNotEmpty();
    }
    // 二次访问命中缓存（仍是 200 + JPEG）
    mockMvc
        .perform(
            get("/api/v1/files/thumbnail")
                .param("path", "shared/photo.png")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
  }

  @Test
  void 非图片文件缩略图被拒() throws Exception {
    Files.writeString(tempRoot.resolve("shared/note.md"), "# 标题");
    mockMvc
        .perform(
            get("/api/v1/files/thumbnail")
                .param("path", "shared/note.md")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("仅图片支持缩略图: shared/note.md"));
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
