package com.guicang.nas.module.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 回收站测试：软删除 → 列表 → 恢复 → 彻底删除（临时存储根）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrashApiTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @Autowired private TrashItemMapper trashItemMapper;

  @MockBean private PAMVerifier pamVerifier;

  private String adminToken;

  private String memberToken;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("shared"));
    Files.writeString(tempRoot.resolve("shared/temp.txt"), "temp");
    Files.createDirectories(tempRoot.resolve("shared/folder"));
    Files.writeString(tempRoot.resolve("shared/folder/inner.md"), "inner");
  }

  @BeforeEach
  void setUp() throws Exception {
    trashItemMapper.delete(null);
    // 测试方法间共享 tempRoot：重建基础文件，清理回收站磁盘目录，保证状态干净
    Files.createDirectories(tempRoot.resolve("shared/folder"));
    if (!Files.exists(tempRoot.resolve("shared/temp.txt"))) {
      Files.writeString(tempRoot.resolve("shared/temp.txt"), "temp");
    }
    if (!Files.exists(tempRoot.resolve("shared/folder/inner.md"))) {
      Files.writeString(tempRoot.resolve("shared/folder/inner.md"), "inner");
    }
    Path trashDir = tempRoot.resolve(".guicang-trash");
    if (Files.exists(trashDir)) {
      try (var walk = Files.walk(trashDir)) {
        walk.sorted(java.util.Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (java.io.IOException ignored) {
                    // 清理尽力而为
                  }
                });
      }
    }
    insertUser("admin", 1L, 1);
    insertUser("bob", 2L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    when(pamVerifier.verify("bob", "Bob-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1004, 2000, "/home/bob", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
    memberToken = login("bob", "Bob-Pass-2026!");
  }

  @Test
  void 删除进回收站并列表可见() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/temp.txt"))
        .andExpect(status().isOk());

    // 原位置已无文件，回收站有记录
    assertThat(Files.exists(tempRoot.resolve("shared/temp.txt"))).isFalse();
    assertThat(trashItemMapper.selectCount(null)).isEqualTo(1);

    mockMvc
        .perform(get("/api/v1/files/trash").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].originalPath").value("shared/temp.txt"))
        .andExpect(jsonPath("$.data[0].username").value("admin"));
  }

  @Test
  void 目录递归删除进回收站() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/folder")
                .param("recursive", "true"))
        .andExpect(status().isOk());

    assertThat(Files.exists(tempRoot.resolve("shared/folder"))).isFalse();
    assertThat(trashItemMapper.selectCount(null)).isEqualTo(1);
  }

  @Test
  void 恢复回原路径() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/temp.txt"))
        .andExpect(status().isOk());

    Long id = trashItemMapper.selectOne(null).getId();

    mockMvc
        .perform(
            post("/api/v1/files/trash/{id}/restore", id)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());

    assertThat(Files.readString(tempRoot.resolve("shared/temp.txt"))).isEqualTo("temp");
    assertThat(trashItemMapper.selectCount(null)).isZero();
  }

  @Test
  void 恢复时原位置被占用则拒绝() throws Exception {
    // 删除后重新创建同名文件
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/temp.txt"))
        .andExpect(status().isOk());
    Files.writeString(tempRoot.resolve("shared/temp.txt"), "new");

    Long id = trashItemMapper.selectOne(null).getId();
    mockMvc
        .perform(
            post("/api/v1/files/trash/{id}/restore", id)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("原位置已存在同名项，请先处理再恢复: shared/temp.txt"));
  }

  @Test
  void 彻底删除清空回收站() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/temp.txt"))
        .andExpect(status().isOk());

    mockMvc
        .perform(delete("/api/v1/files/trash").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());

    assertThat(trashItemMapper.selectCount(null)).isZero();
    try (var trash = Files.list(tempRoot.resolve(".guicang-trash"))) {
      assertThat(trash).isEmpty();
    }
  }

  @Test
  void 他人回收站条目不可操作() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/temp.txt"))
        .andExpect(status().isOk());

    Long id = trashItemMapper.selectOne(null).getId();
    // bob（member）尝试恢复 admin 的条目（admin 名下的条目，bob 非 admin 无权限）
    mockMvc
        .perform(
            post("/api/v1/files/trash/{id}/restore", id)
                .header("Authorization", bearer(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("无权限操作他人回收站条目"));
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
