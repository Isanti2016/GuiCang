package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.file.dto.FileChunkCompleteRequest;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
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

/** 分片断点续传测试：status 返回已传分片、续传后合并成功、未登录拒绝（临时存储根）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChunkResumeTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SysUserMapper sysUserMapper;
  @MockBean private PAMVerifier pamVerifier;

  private String token;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("shared"));
  }

  @BeforeEach
  void setUp() throws Exception {
    sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
    SysUser user = new SysUser();
    user.setUsername("admin");
    user.setDisplayName("admin");
    user.setEnabled(1);
    user.setRoleId(1L);
    user.setHomePath("/home/admin");
    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    sysUserMapper.insert(user);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new com.guicang.nas.module.auth.dto.LoginRequest(
                                "admin", "Admin-Pass-2026!"))))
            .andExpect(status().isOk())
            .andReturn();
    token =
        "Bearer "
            + objectMapper
                .readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
  }

  private void uploadChunk(String uploadId, int index, int total, byte[] data) throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/files/chunk")
                .file(
                    new MockMultipartFile(
                        "file", "part.bin", "application/octet-stream", data))
                .param("path", "shared")
                .param("filename", "big.bin")
                .param("uploadId", uploadId)
                .param("chunkIndex", String.valueOf(index))
                .param("totalChunks", String.valueOf(total))
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));
  }

  @Test
  void 断点续传跳过已传分片并合并() throws Exception {
    String uploadId = "resume-test-001";
    byte[] chunk0 = new byte[1024];
    byte[] chunk1 = new byte[1024];
    byte[] chunk2 = new byte[512];
    chunk0[0] = 1;
    chunk1[0] = 2;
    chunk2[0] = 3;

    // 模拟中断：只传了 0、1 两片
    uploadChunk(uploadId, 0, 3, chunk0);
    uploadChunk(uploadId, 1, 3, chunk1);

    // 查询状态：应返回 [0,1] 与 2048 字节
    mockMvc
        .perform(
            get("/api/v1/files/chunk/status")
                .param("uploadId", uploadId)
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.uploadedChunks[0]").value(0))
        .andExpect(jsonPath("$.data.uploadedChunks[1]").value(1))
        .andExpect(jsonPath("$.data.uploadedBytes").value(2048));

    // 续传第 2 片后合并
    uploadChunk(uploadId, 2, 3, chunk2);
    mockMvc
        .perform(
            post("/api/v1/files/chunk/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new FileChunkCompleteRequest("shared", "big.bin", uploadId, 3)))
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));

    byte[] merged = Files.readAllBytes(tempRoot.resolve("shared/big.bin"));
    assertThat(merged).hasSize(2048 + 512);
    assertThat(merged[0]).isEqualTo((byte) 1);
    assertThat(merged[1024]).isEqualTo((byte) 2);
    assertThat(merged[2048]).isEqualTo((byte) 3);
  }

  @Test
  void 无分片时状态为空() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/files/chunk/status")
                .param("uploadId", "never-exists")
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.uploadedChunks").isEmpty())
        .andExpect(jsonPath("$.data.uploadedBytes").value(0));
  }

  @Test
  void 未登录查询状态被拒401() throws Exception {
    mockMvc
        .perform(get("/api/v1/files/chunk/status").param("uploadId", "x"))
        .andExpect(status().isUnauthorized());
  }
}
