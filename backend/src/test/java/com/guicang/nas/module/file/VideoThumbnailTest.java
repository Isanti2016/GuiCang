package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
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

/**
 * 视频缩略图测试：ffmpeg 抽帧生成 JPEG 封面（ffmpeg 缺失时跳过）。
 *
 * <p>视频 Range 播放能力已在 UploadDownloadTest（206 部分内容）覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VideoThumbnailTest {

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

  private static Path sampleVideo;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("shared"));
    sampleVideo = tempRoot.resolve("sample.mp4");
  }

  @BeforeEach
  void setUp() throws Exception {
    insertUser("admin", 1L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
  }

  @Test
  void 视频生成JPEG封面() throws Exception {
    assumeTrue(ffmpegAvailable(), "ffmpeg 不可用，跳过视频缩略图测试");

    // ffmpeg 生成 1 秒测试视频
    Process generate =
        new ProcessBuilder(
                "ffmpeg",
                "-f",
                "lavfi",
                "-i",
                "color=c=blue:s=320x240:d=2",
                "-pix_fmt",
                "yuv420p",
                "-y",
                sampleVideo.toString())
            .redirectErrorStream(true)
            .start();
    generate.getInputStream().readAllBytes();
    assumeTrue(generate.waitFor(30, TimeUnit.SECONDS) && generate.exitValue() == 0, "测试视频生成失败");

    mockMvc
        .perform(
            multipart("/api/v1/files/upload")
                .file(
                    new MockMultipartFile(
                        "file", "sample.mp4", "video/mp4", Files.readAllBytes(sampleVideo)))
                .param("path", "shared")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/files/thumbnail")
                    .param("path", "shared/sample.mp4")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andReturn();

    byte[] thumb = result.getResponse().getContentAsByteArray();
    assertThat(thumb).isNotEmpty();
    assertThat(thumb[0]).isEqualTo((byte) 0xFF);
    assertThat(thumb[1]).isEqualTo((byte) 0xD8);
  }

  private boolean ffmpegAvailable() {
    try {
      Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
      p.getInputStream().readAllBytes();
      return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
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
