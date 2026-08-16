package com.guicang.nas.module.file;

import com.guicang.nas.common.Result;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.module.file.dto.FileMoveRequest;
import com.guicang.nas.module.file.dto.FilePathRequest;
import com.guicang.nas.module.file.dto.FileRenameRequest;
import com.guicang.nas.module.file.dto.FileStreamInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件管理接口（目录操作 + 上传下载/预览；路径为存储根下相对路径）。 */
@RestController
@RequestMapping("/api/v1/files")
@Validated
public class FileController {

  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  /** 目录列表。 */
  @GetMapping("/list")
  public Result<List<FileEntry>> list(@RequestParam(defaultValue = "") String path) {
    return Result.ok(fileService.list(path));
  }

  /** 新建目录。 */
  @PostMapping("/mkdir")
  public Result<Void> mkdir(@Valid @RequestBody FilePathRequest request) {
    fileService.mkdir(request.path());
    return Result.ok();
  }

  /** 重命名。 */
  @PutMapping("/rename")
  public Result<Void> rename(@Valid @RequestBody FileRenameRequest request) {
    fileService.rename(request.path(), request.newName());
    return Result.ok();
  }

  /** 移动到目录。 */
  @PostMapping("/move")
  public Result<Void> move(@Valid @RequestBody FileMoveRequest request) {
    fileService.move(request.path(), request.target());
    return Result.ok();
  }

  /** 删除（目录默认须为空；recursive=true 递归删除）。 */
  @DeleteMapping
  public Result<Void> delete(
      @RequestParam @NotBlank String path,
      @RequestParam(defaultValue = "false") boolean recursive) {
    fileService.delete(path, recursive);
    return Result.ok();
  }

  /** 上传（multipart，≤1G 流式写盘 + 原子改名）。 */
  @PostMapping("/upload")
  public Result<FileEntry> upload(
      @RequestParam(defaultValue = "") String path, @RequestParam("file") MultipartFile file) {
    return Result.ok(fileService.upload(path, file));
  }

  /** 下载（附件，支持 HTTP Range → 206）。 */
  @GetMapping("/download")
  public ResponseEntity<?> download(@RequestParam @NotBlank String path, HttpServletRequest request)
      throws IOException {
    return respond(fileService.stream(path), request, "attachment");
  }

  /** 预览/流媒体（内联，支持 HTTP Range → 206；视频播放必需）。 */
  @GetMapping("/stream")
  public ResponseEntity<?> stream(@RequestParam @NotBlank String path, HttpServletRequest request)
      throws IOException {
    return respond(fileService.stream(path), request, "inline");
  }

  private ResponseEntity<?> respond(
      FileStreamInfo info, HttpServletRequest request, String disposition) throws IOException {
    FileSystemResource resource = new FileSystemResource(info.path());
    MediaType mediaType = MediaType.parseMediaType(info.contentType());
    String contentDisposition = disposition + "; filename*=UTF-8''" + encode(info.name());

    List<HttpRange> ranges = HttpRange.parseRanges(request.getHeader(HttpHeaders.RANGE));
    if (ranges.isEmpty()) {
      return ResponseEntity.ok()
          .contentType(mediaType)
          .contentLength(info.size())
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
          .body(resource);
    }
    // 手动 Range：bounded 流只输出请求片段（ResourceRegion 转换器不支持视频等 Content-Type）
    HttpRange range = ranges.get(0);
    long start = range.getRangeStart(info.size());
    long end = range.getRangeEnd(info.size());
    long length = end - start + 1;
    FileInputStream in = new FileInputStream(info.path().toFile());
    in.skipNBytes(start);
    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(mediaType)
        .contentLength(length)
        .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + info.size())
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
        .body(new InputStreamResource(new BoundedInputStream(in, length)));
  }

  /** 只输出前 limit 字节的输入流包装（支持流式 Range 读取）。 */
  private static final class BoundedInputStream extends FilterInputStream {
    private final long limit;
    private long count;

    BoundedInputStream(InputStream in, long limit) {
      super(in);
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      if (count >= limit) {
        return -1;
      }
      int b = super.read();
      if (b != -1) {
        count++;
      }
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (count >= limit) {
        return -1;
      }
      int toRead = (int) Math.min(len, limit - count);
      int n = super.read(b, off, toRead);
      if (n > 0) {
        count += n;
      }
      return n;
    }
  }

  private String encode(String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
