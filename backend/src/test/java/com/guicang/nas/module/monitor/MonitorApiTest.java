package com.guicang.nas.module.monitor;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.infra.monitor.HostMetrics;
import com.guicang.nas.infra.monitor.MetricsCollector;
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

/** 监控接口测试：overview 指标、series、权限隔离（monitor.view 仅管理员）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @Autowired private MetricsServiceImpl metricsService;

  @MockBean private PAMVerifier pamVerifier;

  @MockBean private MetricsCollector metricsCollector;

  private String adminToken;

  private String memberToken;

  @BeforeEach
  void setUp() throws Exception {
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
  void overview返回指标快照() throws Exception {
    stubMetrics();
    metricsService.sampleFine();

    mockMvc
        .perform(get("/api/v1/monitor/overview").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.cpu").value(12.5))
        .andExpect(jsonPath("$.data.memTotalKb").value(16000000))
        .andExpect(jsonPath("$.data.diskAvailKb").value(500000000L));
  }

  @Test
  void series返回序列() throws Exception {
    stubMetrics();
    metricsService.sampleFine();

    mockMvc
        .perform(
            get("/api/v1/monitor/series")
                .param("metric", "cpu")
                .param("granularity", "fine")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(
            jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  private void stubMetrics() {
    when(metricsCollector.collect())
        .thenReturn(
            new HostMetrics(
                12.5,
                0.5,
                0.4,
                0.3,
                16000000,
                8000000,
                2000000,
                1000000,
                1000000000L,
                500000000L,
                1000,
                2000,
                3600,
                System.currentTimeMillis()));
  }

  @Test
  void member访问监控被拒403() throws Exception {
    mockMvc
        .perform(get("/api/v1/monitor/overview").header("Authorization", bearer(memberToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ResultCodes.FORBIDDEN));
  }

  @Test
  void 未登录访问被拒401() throws Exception {
    mockMvc.perform(get("/api/v1/monitor/overview")).andExpect(status().isUnauthorized());
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
