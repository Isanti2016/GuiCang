package com.guicang.nas.module.dav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** WebDAV 接口测试：Basic Auth 认证 + PROPFIND/GET/PUT/MKCOL/DELETE/MOVE 核心方法（临时存储根）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DavApiTest {

  @TempDir static Path tempRoot;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("guicang.storage.root", () -> tempRoot.toString());
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private PAMVerifier pamVerifier;

  private String basic;

  @BeforeAll
  static void prepareRoot() throws Exception {
    Files.createDirectories(tempRoot.resolve("media"));
    Files.writeString(tempRoot.resolve("media/hello.txt"), "hello webdav", StandardCharsets.UTF_8);
  }

  @BeforeEach
  void setUp() {
    when(pamVerifier.verify("admin", "Secret-1"))
        .thenReturn(PAMVerifyResult.success(1003, 2000, "/home/admin", "/usr/sbin/nologin"));
    when(pamVerifier.verify("admin", "wrong")).thenReturn(PAMVerifyResult.failure("bad password"));
    basic = "Basic " + Base64.getEncoder().encodeToString("admin:Secret-1".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mockMvc
        .perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Basic realm=\"GuiCang NAS\""));
  }

  @Test
  void wrongPasswordReturns401() throws Exception {
    String bad = "Basic " + Base64.getEncoder().encodeToString("admin:wrong".getBytes(StandardCharsets.UTF_8));
    mockMvc
        .perform(request(HttpMethod.valueOf("PROPFIND"), "/dav/").header("Authorization", bad))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void propfindRootReturnsMultistatus() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("PROPFIND"), "/dav/")
                .header("Authorization", basic)
                .header("Depth", "0"))
        .andExpect(status().isMultiStatus())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("multistatus")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("<D:href>/dav/</D:href>")));
  }

  @Test
  void propfindDepth1ListsChildren() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("PROPFIND"), "/dav/media/")
                .header("Authorization", basic)
                .header("Depth", "1"))
        .andExpect(status().isMultiStatus())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("hello.txt")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("getcontentlength")));
  }

  @Test
  void propfindMissingReturns404() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("PROPFIND"), "/dav/nope/")
                .header("Authorization", basic))
        .andExpect(status().isNotFound());
  }

  @Test
  void mkcolCreatesDirectory() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("MKCOL"), "/dav/newdir")
                .header("Authorization", basic))
        .andExpect(status().isCreated());
    assertThat(Files.isDirectory(tempRoot.resolve("newdir"))).isTrue();
  }

  @Test
  void putWritesFile() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("PUT"), "/dav/upload/note.txt")
                .header("Authorization", basic)
                .contentType(MediaType.TEXT_PLAIN)
                .content("写入的内容"))
        .andExpect(status().isOk());
    assertThat(Files.readString(tempRoot.resolve("upload/note.txt"), StandardCharsets.UTF_8))
        .isEqualTo("写入的内容");
  }

  @Test
  void getDownloadsContent() throws Exception {
    mockMvc
        .perform(get("/dav/media/hello.txt").header("Authorization", basic))
        .andExpect(status().isOk())
        .andExpect(content().string("hello webdav"));
  }

  @Test
  void deleteRemovesFile() throws Exception {
    Files.writeString(tempRoot.resolve("doomed.txt"), "x", StandardCharsets.UTF_8);
    mockMvc
        .perform(
            request(HttpMethod.valueOf("DELETE"), "/dav/doomed.txt")
                .header("Authorization", basic))
        .andExpect(status().isNoContent());
    assertThat(Files.exists(tempRoot.resolve("doomed.txt"))).isFalse();
  }

  @Test
  void moveRelocatesFile() throws Exception {
    Files.writeString(tempRoot.resolve("src.txt"), "moved", StandardCharsets.UTF_8);
    mockMvc
        .perform(
            request(HttpMethod.valueOf("MOVE"), "/dav/src.txt")
                .header("Authorization", basic)
                .header("Destination", "/dav/dest.txt"))
        .andExpect(status().isCreated());
    assertThat(Files.exists(tempRoot.resolve("dest.txt"))).isTrue();
    assertThat(Files.exists(tempRoot.resolve("src.txt"))).isFalse();
  }

  @Test
  void optionsAnnouncesDavSupport() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("OPTIONS"), "/dav/")
                .header("Authorization", basic))
        .andExpect(status().isOk())
        .andExpect(header().string("DAV", "1"));
  }

  @Test
  void unknownMethodRejected() throws Exception {
    mockMvc
        .perform(
            request(HttpMethod.valueOf("PATCH"), "/dav/media/hello.txt")
                .header("Authorization", basic))
        .andExpect(status().isMethodNotAllowed());
  }
}
