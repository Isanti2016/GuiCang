package com.guicang.nas.module.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
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

/** 审计查询测试：筛选/分页/权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @Autowired private AuditLogMapper auditLogMapper;

  @MockBean private PAMVerifier pamVerifier;

  private String adminToken;

  private String memberToken;

  @BeforeEach
  void setUp() throws Exception {
    auditLogMapper.delete(null);
    insertUser("admin", 1L, 1);
    insertUser("bob", 2L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    when(pamVerifier.verify("bob", "Bob-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1004, 2000, "/home/bob", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
    memberToken = login("bob", "Bob-Pass-2026!");
    // 清掉登录产生的审计，保证各用例数据可控
    auditLogMapper.delete(null);
  }

  @Test
  void 按用户名筛选并分页() throws Exception {
    insertAudit("admin", "login", "success");
    insertAudit("admin", "file.upload", "success");
    insertAudit("bob", "file.write", "failed");

    mockMvc
        .perform(
            get("/api/v1/audit/logs")
                .param("username", "admin")
                .param("page", "1")
                .param("size", "10")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2))
        .andExpect(jsonPath("$.data.records.length()").value(2));

    mockMvc
        .perform(
            get("/api/v1/audit/logs")
                .param("result", "failed")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.records[0].action").value("file.write"));
  }

  @Test
  void 用户名与动作模糊搜索() throws Exception {
    insertAudit("admin", "file.upload", "success");
    insertAudit("admin", "user.create", "success");
    insertAudit("bob", "file.write", "failed");

    // 用户名部分匹配：ad 命中 admin 的 2 条
    mockMvc
        .perform(
            get("/api/v1/audit/logs")
                .param("username", "ad")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));

    // 动作部分匹配：file 命中 file.upload 与 file.write 共 2 条
    mockMvc
        .perform(
            get("/api/v1/audit/logs")
                .param("action", "file")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));
  }

  @Test
  void 分页截断() throws Exception {
    for (int i = 0; i < 5; i++) {
      insertAudit("admin", "login", "success");
    }
    mockMvc
        .perform(
            get("/api/v1/audit/logs")
                .param("page", "1")
                .param("size", "2")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(5))
        .andExpect(jsonPath("$.data.records.length()").value(2));
  }

  @Test
  void 成员访问审计被拒403() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit/logs").header("Authorization", bearer(memberToken)))
        .andExpect(status().isForbidden());
  }

  private void insertAudit(String username, String action, String result) {
    AuditLog log = new AuditLog();
    log.setUsername(username);
    log.setAction(action);
    log.setResource("r");
    log.setResult(result);
    log.setCreatedAt(System.currentTimeMillis());
    auditLogMapper.insert(log);
    assertThat(log.getId()).isNotNull();
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
