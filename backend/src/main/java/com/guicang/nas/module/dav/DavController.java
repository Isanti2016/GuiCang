package com.guicang.nas.module.dav;

import com.guicang.nas.common.BizException;
import com.guicang.nas.infra.account.PAMVerifier;
import com.guicang.nas.infra.account.PAMVerifyResult;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.infra.storage.FileTypeUtils;
import com.guicang.nas.infra.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** WebDAV 控制器：Basic Auth（PAM）+ 核心方法（PROPFIND/GET/PUT/MKCOL/DELETE/MOVE）。 */
@RestController
public class DavController {

  private final StorageService storageService;
  private final PAMVerifier pamVerifier;

  public DavController(StorageService storageService, PAMVerifier pamVerifier) {
    this.storageService = storageService;
    this.pamVerifier = pamVerifier;
  }

  @RequestMapping("/dav/**")
  public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (authenticate(request, response) == null) {
      return;
    }
    String method = request.getMethod();
    String path = extractPath(request);
    switch (method) {
      case "OPTIONS" -> handleOptions(response);
      case "PROPFIND" -> handlePropfind(path, request, response);
      case "GET" -> handleGet(path, response);
      case "PUT" -> handlePut(path, request);
      case "MKCOL" -> handleMkcol(path, response);
      case "DELETE" -> handleDelete(path, response);
      case "MOVE" -> handleMove(path, request, response);
      default -> response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  }

  /**
   * 显式映射 OPTIONS：Spring MVC 对"方法条件为空"的映射会自动生成 HttpOptionsHandler 并短路
   * （响应体只有默认 Allow 头，没有 DAV 头），导致 WebDAV 客户端无法发现 DAV 支持。
   * 显式声明 method=OPTIONS 后，请求会路由到本方法而不是自动 handler。
   */
  @RequestMapping(value = "/dav/**", method = RequestMethod.OPTIONS)
  public void handleOptionsRoute(HttpServletResponse response) {
    handleOptions(response);
  }

  private String authenticate(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Basic ")) {
      String decoded =
          new String(Base64.getDecoder().decode(auth.substring(6)), StandardCharsets.UTF_8);
      String[] parts = decoded.split(":", 2);
      if (parts.length == 2) {
        PAMVerifyResult r = pamVerifier.verify(parts[0], parts[1]);
        if (r != null && r.ok()) {
          return parts[0];
        }
      }
    }
    response.setHeader("WWW-Authenticate", "Basic realm=\"GuiCang NAS\"");
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    return null;
  }

  private String extractPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String path = uri.substring(contextPath.length());
    if (path.startsWith("/dav")) {
      path = path.substring(4);
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    try {
      path = URLDecoder.decode(path, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
    }
    return path;
  }

  private void handleOptions(HttpServletResponse response) {
    response.setHeader("DAV", "1");
    response.setHeader("Allow", "OPTIONS, PROPFIND, GET, PUT, MKCOL, DELETE, MOVE");
    response.setStatus(HttpServletResponse.SC_OK);
  }

  private void handlePropfind(String path, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String depth = request.getHeader("Depth");
    Path abs;
    try {
      abs = path.isBlank() ? storageService.root() : storageService.resolvePath(path);
    } catch (BizException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
    xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");
    appendResource(xml, path, abs);
    if ("1".equals(depth) && Files.isDirectory(abs)) {
      for (FileEntry e : storageService.list(path)) {
        appendResource(xml, e.path(), storageService.resolvePath(e.path()));
      }
    }
    xml.append("</D:multistatus>");
    response.setStatus(207);
    response.setContentType("application/xml; charset=utf-8");
    response.getWriter().write(xml.toString());
  }

  private void appendResource(StringBuilder xml, String path, Path abs) {
    boolean dir = Files.isDirectory(abs);
    String href = "/dav/" + (path.isEmpty() ? "" : path + (dir ? "/" : ""));
    xml.append("  <D:response>\n");
    xml.append("    <D:href>").append(escapeXml(href)).append("</D:href>\n");
    xml.append("    <D:propstat>\n");
    xml.append("      <D:prop>\n");
    xml.append("        <D:resourcetype>")
        .append(dir ? "<D:collection/>" : "")
        .append("</D:resourcetype>\n");
    if (!dir) {
      try {
        xml.append("        <D:getcontentlength>")
            .append(Files.size(abs))
            .append("</D:getcontentlength>\n");
      } catch (IOException ignored) {
      }
    }
    xml.append("        <D:getlastmodified>")
        .append(formatLastModified(abs))
        .append("</D:getlastmodified>\n");
    xml.append("      </D:prop>\n");
    xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
    xml.append("    </D:propstat>\n");
    xml.append("  </D:response>\n");
  }

  private String formatLastModified(Path abs) {
    try {
      return ZonedDateTime.ofInstant(
              Files.getLastModifiedTime(abs).toInstant(), ZoneId.of("GMT"))
          .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    } catch (IOException e) {
      return "Thu, 01 Jan 1970 00:00:00 GMT";
    }
  }

  private void handleGet(String path, HttpServletResponse response) throws IOException {
    Path abs;
    try {
      abs = storageService.resolvePath(path);
    } catch (BizException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    if (Files.isDirectory(abs)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    response.setContentType(FileTypeUtils.contentType(abs.getFileName().toString()));
    response.setContentLengthLong(Files.size(abs));
    try (InputStream in = Files.newInputStream(abs);
        OutputStream out = response.getOutputStream()) {
      in.transferTo(out);
    }
  }

  private void handlePut(String path, HttpServletRequest request) throws IOException {
    Path target = storageService.resolveForWrite(path);
    if (target.getParent() != null) {
      Files.createDirectories(target.getParent());
    }
    Path tmp = Files.createTempFile(target.getParent(), ".dav-", ".tmp");
    try (InputStream in = request.getInputStream();
        OutputStream out = Files.newOutputStream(tmp)) {
      in.transferTo(out);
    }
    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
  }

  private void handleMkcol(String path, HttpServletResponse response) throws IOException {
    try {
      storageService.mkdir(path);
      response.setStatus(HttpServletResponse.SC_CREATED);
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  }

  private void handleDelete(String path, HttpServletResponse response) throws IOException {
    try {
      storageService.delete(path, true);
      response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private void handleMove(String path, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String dest = request.getHeader("Destination");
    if (dest == null || dest.isBlank()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    String destPath = extractDestPath(dest);
    try {
      storageService.moveTo(path, destPath);
      response.setStatus(HttpServletResponse.SC_CREATED);
    } catch (Exception e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private String extractDestPath(String dest) {
    String d = dest;
    int idx = d.indexOf("/dav/");
    if (idx >= 0) {
      d = d.substring(idx + 5);
    }
    if (d.startsWith("/")) {
      d = d.substring(1);
    }
    if (d.endsWith("/")) {
      d = d.substring(0, d.length() - 1);
    }
    try {
      d = URLDecoder.decode(d, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
    }
    return d;
  }

  private String escapeXml(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
