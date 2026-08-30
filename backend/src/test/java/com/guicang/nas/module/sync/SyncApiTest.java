package com.guicang.nas.module.sync;

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
import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexMapper;
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

/** 同步任务 API 测试：创建/立即执行/历史/权限（临时存储根）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncApiTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysUserMapper sysUserMapper;

  @Autowired private FileIndexMapper fileIndexMapper;

  @MockBean private PAMVerifier pamVerifier;

  private String adminToken;

  private String memberToken;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("media/photos"));
    Files.writeString(tempRoot.resolve("media/photos/pic.jpg"), "data");
  }

  @BeforeEach
  void setUp() throws Exception {
    fileIndexMapper.delete(null);
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
  void 创建任务立即执行并更新索引() throws Exception {
    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/sync/tasks")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"照片同步\",\"sourceConfig\":\"media/photos\","
                            + "\"cron\":\"0 0 3 * * ?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
            .andReturn();

    long taskId =
        objectMapper
            .readTree(create.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

    // 立即执行
    mockMvc
        .perform(
            post("/api/v1/sync/tasks/{id}/run", taskId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("success"))
        .andExpect(jsonPath("$.data.added").value(1));

    // 索引已更新
    assertThat(
            fileIndexMapper.selectCount(
                new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getPath, "media/photos/pic.jpg")))
        .isEqualTo(1);

    // 历史可查
    mockMvc
        .perform(
            get("/api/v1/sync/history")
                .param("taskId", String.valueOf(taskId))
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].status").value("success"));
  }

  @Test
  void 非法cron被拒() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sync/tasks")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"sourceConfig\":\"media\",\"cron\":\"not-a-cron\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("cron 表达式不合法（Quartz 6 段格式）"));
  }

  @Test
  void 删除任务后不可再执行() throws Exception {
    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/sync/tasks")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"t\",\"sourceConfig\":\"media\",\"cron\":\"0 0 3 * * ?\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long taskId =
        objectMapper
            .readTree(create.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

    mockMvc
        .perform(
            delete("/api/v1/sync/tasks/{id}", taskId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/sync/tasks/{id}/run", taskId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 成员访问同步被拒403() throws Exception {
    mockMvc
        .perform(get("/api/v1/sync/tasks").header("Authorization", bearer(memberToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void 创建自动整理任务并立即执行移动文件() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox-a"));
    Files.writeString(tempRoot.resolve("inbox-a/photo.jpg"), "x");

    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/sync/tasks")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"照片整理\",\"sourceConfig\":\"inbox-a\","
                            + "\"targetConfig\":\"photos\",\"taskType\":\"organize\","
                            + "\"ruleType\":\"kind\",\"action\":\"move\",\"conflict\":\"rename\","
                            + "\"cron\":\"0 0 3 * * ?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.taskType").value("organize"))
            .andReturn();

    long taskId =
        objectMapper
            .readTree(create.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

    mockMvc
        .perform(
            post("/api/v1/sync/tasks/{id}/run", taskId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("success"))
        .andExpect(jsonPath("$.data.succeeded").value(1))
        .andExpect(jsonPath("$.data.processed").value(1));

    // 文件已移动并建立索引
    assertThat(Files.exists(tempRoot.resolve("inbox-a/photo.jpg"))).isFalse();
    assertThat(Files.exists(tempRoot.resolve("photos/image/photo.jpg"))).isTrue();
    assertThat(
            fileIndexMapper.selectCount(
                new LambdaQueryWrapper<FileIndex>()
                    .eq(FileIndex::getPath, "photos/image/photo.jpg")))
        .isEqualTo(1);
  }

  @Test
  void 自动整理目标为源子目录被拒() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/sync/tasks")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"非法\",\"sourceConfig\":\"media\","
                        + "\"targetConfig\":\"media/sub\",\"taskType\":\"organize\","
                        + "\"ruleType\":\"kind\",\"cron\":\"0 0 3 * * ?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("目标目录不能是源目录或其子目录（防循环整理）"));
  }

  @Test
  void 自动整理目标为源子目录带点号被拒() throws Exception {
    // media/sub/../sub2 规范化后仍是 media/sub2，属 media 子目录 → 应被拒
    mockMvc
        .perform(
            post("/api/v1/sync/tasks")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"非法2\",\"sourceConfig\":\"media\","
                        + "\"targetConfig\":\"media/sub/../sub2\",\"taskType\":\"organize\","
                        + "\"ruleType\":\"kind\",\"cron\":\"0 0 3 * * ?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("目标目录不能是源目录或其子目录（防循环整理）"));
  }

  @Test
  void 自动整理编辑保留目标目录() throws Exception {
    Files.createDirectories(tempRoot.resolve("inbox-b"));
    Files.writeString(tempRoot.resolve("inbox-b/pic.jpg"), "x");
    MvcResult create =
        mockMvc
            .perform(
                post("/api/v1/sync/tasks")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"整理\",\"sourceConfig\":\"inbox-b\","
                            + "\"targetConfig\":\"photos\",\"taskType\":\"organize\","
                            + "\"ruleType\":\"kind\",\"action\":\"move\",\"conflict\":\"rename\","
                            + "\"cron\":\"0 0 3 * * ?\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long taskId =
        objectMapper
            .readTree(create.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

    // 编辑时前端可能只传部分字段（targetConfig 为空串），后端应保留原值
    mockMvc
        .perform(
            put("/api/v1/sync/tasks/{id}", taskId)
                .param("enabled", "true")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"整理\",\"sourceConfig\":\"inbox-b\",\"cron\":\"0 0 4 * * ?\","
                        + "\"taskType\":\"organize\",\"targetConfig\":\"\",\"ruleType\":\"kind\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetConfig").value("photos"))
        .andExpect(jsonPath("$.data.cron").value("0 0 4 * * ?"));
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
