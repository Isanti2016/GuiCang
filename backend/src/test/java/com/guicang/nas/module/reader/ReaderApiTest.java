package com.guicang.nas.module.reader;

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
import com.guicang.nas.module.auth.dto.LoginRequest;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

/** 小说阅读 API 测试：打开/读章/进度/权限/格式拒绝（临时存储根）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReaderApiTest {

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

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("books"));
    Files.writeString(
        tempRoot.resolve("books/仙路风云.txt"),
        "第一章 重生归来\n正文一。\n第二章 初次修炼\n正文二。\n第三章 下山\n正文三。\n",
        StandardCharsets.UTF_8);
    Files.writeString(tempRoot.resolve("books/readme.md"), "# 说明\n", StandardCharsets.UTF_8);
    Files.write(tempRoot.resolve("books/封面.jpg"), new byte[] {1, 2, 3});
    buildEpub(
        tempRoot.resolve("books/剑来.epub"),
        Map.of(
            "META-INF/container.xml",
            "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\"/></rootfiles></container>",
            "OEBPS/content.opf",
            "<?xml version=\"1.0\"?><package><metadata><dc:title>剑来</dc:title></metadata>"
                + "<manifest><item id=\"c1\" href=\"c1.xhtml\"/></manifest>"
                + "<spine><itemref idref=\"c1\"/></spine></package>",
            "OEBPS/c1.xhtml",
            "<html><body><h1>第一章 陈平安</h1><p>泥瓶巷。</p></body></html>"));
  }

  @BeforeEach
  void setUp() throws Exception {
    insertUser("admin", 1L, 1);
    when(pamVerifier.verify("admin", "Admin-Pass-2026!"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    adminToken = login("admin", "Admin-Pass-2026!");
  }

  @Test
  void 打开txt书籍() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/novel")
                .param("path", "books/仙路风云.txt")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.format").value("TXT"))
        .andExpect(jsonPath("$.data.chapterCount").value(3))
        .andExpect(jsonPath("$.data.title").value("仙路风云"))
        .andExpect(jsonPath("$.data.encoding").value("UTF-8"))
        .andExpect(jsonPath("$.data.chapters[1].title").value("第二章 初次修炼"));
  }

  @Test
  void 读取章节正文() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/chapter")
                .param("path", "books/仙路风云.txt")
                .param("index", "1")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.index").value(1))
        .andExpect(jsonPath("$.data.total").value(3))
        .andExpect(jsonPath("$.data.title").value("第二章 初次修炼"))
        .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("正文二")));
  }

  @Test
  void 章节越界被拒() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/chapter")
                .param("path", "books/仙路风云.txt")
                .param("index", "99")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 打开epub书籍() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/novel")
                .param("path", "books/剑来.epub")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.format").value("EPUB"))
        .andExpect(jsonPath("$.data.title").value("剑来"))
        .andExpect(jsonPath("$.data.chapterCount").value(1))
        .andExpect(jsonPath("$.data.chapters[0].title").value("第一章 陈平安"));
  }

  @Test
  void 不支持格式被拒() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/novel")
                .param("path", "books/封面.jpg")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("暂不支持该格式")));
  }

  @Test
  void 未登录返回401() throws Exception {
    mockMvc
        .perform(get("/api/v1/reader/novel").param("path", "books/仙路风云.txt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 路径穿越被拒() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/novel")
                .param("path", "../../../etc/passwd")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR));
  }

  @Test
  void 进度保存与读取() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/reader/progress")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("path", "books/仙路风云.txt", "chapterIndex", 2, "percent", 65))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS));

    mockMvc
        .perform(
            get("/api/v1/reader/progress")
                .param("path", "books/仙路风云.txt")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.chapterIndex").value(2))
        .andExpect(jsonPath("$.data.percent").value(65));
  }

  @Test
  void 进度参数越界被拒() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/reader/progress")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("path", "books/仙路风云.txt", "chapterIndex", 0, "percent", 150))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 未读过书籍进度为空() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/reader/progress")
                .param("path", "books/剑来.epub")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
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
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
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

  private static void buildEpub(Path file, Map<String, String> entries) throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      for (Map.Entry<String, String> e : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(e.getKey()));
        out.write(e.getValue().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
  }
}
