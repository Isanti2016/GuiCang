package com.guicang.nas.module.dashboard;

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
import com.guicang.nas.module.audit.AuditLog;
import com.guicang.nas.module.audit.AuditLogMapper;
import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexMapper;
import com.guicang.nas.module.monitor.MetricsServiceImpl;
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

/** 大屏聚合测试：文件统计/用户数/最近操作/权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @Autowired private FileIndexMapper fileIndexMapper;

  @Autowired private AuditLogMapper auditLogMapper;

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
  void summary聚合统计() throws Exception {
    // 文件索引数据
    insertIndex("shared/photo.png", "image");
    insertIndex("shared/video.mp4", "video");
    insertIndex("shared/note.md", "note");
    insertIndex("shared", "dir");
    // 审计记录
    AuditLog audit = new AuditLog();
    audit.setUsername("admin");
    audit.setAction("login");
    audit.setResult("success");
    audit.setCreatedAt(System.currentTimeMillis());
    auditLogMapper.insert(audit);
    // 指标
    when(metricsCollector.collect())
        .thenReturn(
            new HostMetrics(
                10,
                1,
                1,
                1,
                16000000,
                8000000,
                0,
                0,
                1000000,
                500000,
                0,
                0,
                3600,
                System.currentTimeMillis()));
    metricsService.sampleFine();

    mockMvc
        .perform(get("/api/v1/dashboard/summary").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.fileTotal").value(4))
        .andExpect(jsonPath("$.data.fileImages").value(1))
        .andExpect(jsonPath("$.data.fileVideos").value(1))
        .andExpect(jsonPath("$.data.fileNotes").value(1))
        .andExpect(jsonPath("$.data.userTotal").value(2))
        .andExpect(jsonPath("$.data.diskUsedPercent").value(50))
        .andExpect(jsonPath("$.data.recentOperations[0].action").value("login"));
  }

  @Test
  void 未登录访问被拒401() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
  }

  private void insertIndex(String path, String kind) {
    FileIndex index = new FileIndex();
    index.setPath(path);
    index.setName(path.substring(path.lastIndexOf('/') + 1));
    index.setKind(kind);
    index.setSize(10L);
    index.setMtime(System.currentTimeMillis());
    index.setIndexedAt(System.currentTimeMillis());
    fileIndexMapper.delete(new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getPath, path));
    fileIndexMapper.insert(index);
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
