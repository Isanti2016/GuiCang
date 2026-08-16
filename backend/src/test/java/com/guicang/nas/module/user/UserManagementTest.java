package com.guicang.nas.module.user;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.guicang.nas.infra.account.ProvisionResult;
import com.guicang.nas.infra.account.SystemAccountProvisioner;
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

/** 用户管理（RBAC）测试：管理员 CRUD、权限隔离、系统账号同步、停用/未开通登录拒绝。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @MockBean private PAMVerifier pamVerifier;

  @MockBean private SystemAccountProvisioner systemAccountProvisioner;

  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    insertUser("admin", "admin", 1L, 1);
    insertUser("bob", "成员", 2L, 1);
    when(pamVerifier.verify("admin", "AdminPass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    when(pamVerifier.verify("bob", "BobPass-2026!"))
        .thenReturn(PAMVerifyResult.success(1004, 2000, "/home/bob", "/usr/sbin/nologin"));
    adminToken = login("admin", "AdminPass-2026!");
  }

  @Test
  void 管理员可访问用户列表() throws Exception {
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));
  }

  @Test
  void 成员访问用户管理被拒403() throws Exception {
    String memberToken = login("bob", "BobPass-2026!");
    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(memberToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ResultCodes.FORBIDDEN));
  }

  @Test
  void 管理员创建用户并同步系统账号() throws Exception {
    when(systemAccountProvisioner.createUser(eq("alice"), anyString()))
        .thenReturn(ProvisionResult.created(1005L));

    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"alice\",\"displayName\":\"爱丽丝\","
                        + "\"password\":\"AlicePass-2026!\",\"roleId\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.username").value("alice"))
        .andExpect(jsonPath("$.data.roleCode").value("member"))
        .andExpect(jsonPath("$.data.enabled").value(true));

    SysUser created =
        sysUserMapper.selectOne(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "alice"));
    assert created != null && created.getUid() == 1005L;
  }

  @Test
  void helper未部署时创建用户失败() throws Exception {
    when(systemAccountProvisioner.createUser(eq("dave"), anyString()))
        .thenReturn(ProvisionResult.pending("guicang-helper 未部署"));

    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"dave\",\"displayName\":\"戴夫\","
                        + "\"password\":\"DavePass-2026!\",\"roleId\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("系统账号服务未就绪（guicang-helper 未部署），用户创建失败"));
  }

  @Test
  void 停用内置admin被拒() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/admin/status")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("内置 admin 账号不可停用"));
  }

  @Test
  void 未开通Web访问的系统账号登录被拒() throws Exception {
    when(pamVerifier.verify("eve", "EvePass-2026!"))
        .thenReturn(PAMVerifyResult.success(1006, 2000, "/home/eve", "/usr/sbin/nologin"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"eve\",\"password\":\"EvePass-2026!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED))
        .andExpect(jsonPath("$.message").value("账号未开通或已停用"));
  }

  @Test
  void 停用用户登录被拒() throws Exception {
    insertUser("carol", "卡罗尔", 2L, 0);
    when(pamVerifier.verify("carol", "CarolPass-2026!"))
        .thenReturn(PAMVerifyResult.success(1007, 2000, "/home/carol", "/usr/sbin/nologin"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"carol\",\"password\":\"CarolPass-2026!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.UNAUTHORIZED))
        .andExpect(jsonPath("$.message").value("账号未开通或已停用"));
  }

  @Test
  void 成员登录me返回member角色() throws Exception {
    String memberToken = login("bob", "BobPass-2026!");
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(memberToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("bob"))
        .andExpect(jsonPath("$.data.roles[0]").value("ROLE_MEMBER"));
  }

  private void insertUser(String username, String displayName, Long roleId, int enabled) {
    sysUserMapper.delete(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    SysUser user = new SysUser();
    user.setUsername(username);
    user.setDisplayName(displayName);
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
