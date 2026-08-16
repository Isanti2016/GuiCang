package com.guicang.nas.module.user;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MvcResult;

/** 角色管理测试：CRUD、内置角色保护、删除占用拒绝、权限点列表。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @Autowired private SysRoleMapper sysRoleMapper;

  @MockBean private PAMVerifier pamVerifier;

  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    // 清理测试残留的自定义角色（内置 3 个保留）
    sysRoleMapper.delete(
        new LambdaQueryWrapper<SysRole>().notIn(SysRole::getCode, "admin", "member", "guest"));
    sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "tempuser"));
    insertUser("admin", 1L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
  }

  @Test
  void 创建角色并返回权限编码() throws Exception {
    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/roles")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"editor\",\"name\":\"编辑者\","
                            + "\"description\":\"仅文件\",\"permissionIds\":[5,6]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
            .andExpect(jsonPath("$.data.code").value("editor"))
            .andExpect(jsonPath("$.data.permissionCodes[0]").value("file.read"))
            .andReturn();

    assertThat(create.getResponse().getContentAsString()).contains("file.read", "file.write");
  }

  @Test
  void 角色列表含内置角色与权限() throws Exception {
    mockMvc
        .perform(get("/api/v1/roles").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].code").value("admin"))
        .andExpect(jsonPath("$.data[0].permissionCodes.length()").value(10));
  }

  @Test
  void 内置角色编码不可修改() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/roles/1")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"super\",\"name\":\"超管\","
                        + "\"description\":\"x\",\"permissionIds\":[1]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("内置角色编码不可修改"));
  }

  @Test
  void 内置角色不可删除() throws Exception {
    mockMvc
        .perform(delete("/api/v1/roles/1").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("内置角色不可删除"));
  }

  @Test
  void 已分配角色不可删除() throws Exception {
    // 创建自定义角色
    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/roles")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"temp\",\"name\":\"临时\","
                            + "\"description\":\"x\",\"permissionIds\":[9]}"))
            .andExpect(status().isOk())
            .andReturn();
    long roleId =
        objectMapper
            .readTree(create.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

    // 分配给用户
    insertUser("tempuser", roleId, 1);

    mockMvc
        .perform(delete("/api/v1/roles/{id}", roleId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("角色已分配给用户，不可删除"));
  }

  @Test
  void 权限点列表() throws Exception {
    mockMvc
        .perform(get("/api/v1/permissions").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(10))
        .andExpect(jsonPath("$.data[0].code").value("user.manage"));
  }

  @Test
  void 成员访问角色被拒403() throws Exception {
    insertUser("bob", 2L, 1);
    when(pamVerifier.verify("bob", "Bob-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1004, 2000, "/home/bob", "/usr/sbin/nologin"));
    String memberToken = login("bob", "Bob-Pass-2026!");
    mockMvc
        .perform(get("/api/v1/roles").header("Authorization", bearer(memberToken)))
        .andExpect(status().isForbidden());
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
