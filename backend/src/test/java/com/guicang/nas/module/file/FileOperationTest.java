package com.guicang.nas.module.file;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 文件目录操作端到端测试：mkdir/list/rename/move/delete + 越权拒绝（存储根为临时目录）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileOperationTest {

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
  void 完整目录操作流程() throws Exception {
    // mkdir 多级
    mockMvc
        .perform(
            post("/api/v1/files/mkdir")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/team/docs\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));

    // list 根目录包含 shared
    mockMvc
        .perform(get("/api/v1/files/list").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[?(@.name=='shared')].dir").value(true));

    // 非空目录非递归删除被拒
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/team"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));

    // rename
    mockMvc
        .perform(
            put("/api/v1/files/rename")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/team/docs\",\"newName\":\"notes\"}"))
        .andExpect(status().isOk());

    // move 到根目录（"." 表示存储根）
    mockMvc
        .perform(
            post("/api/v1/files/move")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"shared/team/notes\",\"target\":\".\"}"))
        .andExpect(status().isOk());

    // 递归删除（team 现已空，非递归也能删；这里验证递归路径）
    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(adminToken))
                .param("path", "shared/team")
                .param("recursive", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));
  }

  @Test
  void guest只能读shared不能删除() throws Exception {
    mockMvc
        .perform(get("/api/v1/files/list").header("Authorization", bearer(guestToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/api/v1/files")
                .header("Authorization", bearer(guestToken))
                .param("path", "shared")
                .param("recursive", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 目录穿越被拒() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/files/list")
                .header("Authorization", bearer(adminToken))
                .param("path", "../secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 未登录访问被拒401() throws Exception {
    mockMvc
        .perform(get("/api/v1/files/list"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED));
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
