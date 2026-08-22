package com.guicang.nas.module.file;

import com.guicang.nas.module.file.dto.FileStreamInfo;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.stereotype.Component;

/**
 * 文件下载/流媒体响应组装器：统一处理 Content-Disposition、HTTP Range（206 分段）与限长流包装。
 *
 * <p>将响应构造细节从 Controller 中剥离，使 Controller 只保留请求接收与转发职责。
 */
@Component
public class FileStreamResponder {

  /**
   * 按下载/内联模式组装文件响应，支持 HTTP Range 分段（视频拖动播放必需）。
   *
   * @param info 文件流信息（路径、大小、Content-Type、文件名）
   * @param request 原始请求（用于解析 Range 头）
   * @param disposition 响应模式：attachment=下载，inline=内联预览
   * @return 完整文件响应（200 或 206 分段）
   * @throws IOException 文件读取失败时抛出
   */
  public ResponseEntity<?> respond(
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

  /**
   * 将文件名编码进 Content-Disposition（RFC 5987：UTF-8 百分号编码 + 空格转 %20）。
   *
   * @param value 原始文件名
   * @return 编码后的文件名
   * @throws UnsupportedEncodingException UTF-8 编码不可用时抛出
   */
  private String encode(String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
}
