package com.guicang.nas.module.file;

import com.guicang.nas.common.Result;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.module.file.dto.FileBatchDeleteRequest;
import com.guicang.nas.module.file.dto.FileMoveRequest;
import com.guicang.nas.module.file.dto.FilePathRequest;
import com.guicang.nas.module.file.dto.FileRenameRequest;
import com.guicang.nas.module.file.dto.FileWriteRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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

/** 文件管理接口：目录操作 + 上传下载/预览；路径为存储根下相对路径。只做请求接收与转发。 */
@RestController
@RequestMapping("/api/v1/files")
@Validated
public class FileController {

  private final FileService fileService;
  private final FileStreamResponder fileStreamResponder;

  public FileController(FileService fileService, FileStreamResponder fileStreamResponder) {
    this.fileService = fileService;
    this.fileStreamResponder = fileStreamResponder;
  }

  /**
   * 目录列表。
   *
   * @param path 相对路径，空表示存储根
   * @return 目录条目列表
   */
  @GetMapping("/list")
  public Result<List<FileEntry>> list(@RequestParam(defaultValue = "") String path) {
    return Result.ok(fileService.list(path));
  }

  /**
   * 递归收集图片/视频（相册数据源）。
   *
   * @param path 起始相对路径，空表示存储根
   * @return 图片/视频文件条目列表
   */
  @GetMapping("/media")
  public Result<List<FileEntry>> media(@RequestParam(defaultValue = "") String path) {
    return Result.ok(fileService.media(path));
  }

  /**
   * 新建目录。
   *
   * @param request 目录路径请求体
   * @return 空结果
   */
  @PostMapping("/mkdir")
  public Result<Void> mkdir(@Valid @RequestBody FilePathRequest request) {
    fileService.mkdir(request.path());
    return Result.ok();
  }

  /**
   * 重命名。
   *
   * @param request 重命名请求体（路径 + 新名称）
   * @return 空结果
   */
  @PutMapping("/rename")
  public Result<Void> rename(@Valid @RequestBody FileRenameRequest request) {
    fileService.rename(request.path(), request.newName());
    return Result.ok();
  }

  /**
   * 移动到目录。
   *
   * @param request 移动请求体（源路径 + 目标目录）
   * @return 空结果
   */
  @PostMapping("/move")
  public Result<Void> move(@Valid @RequestBody FileMoveRequest request) {
    fileService.move(request.path(), request.target());
    return Result.ok();
  }

  /**
   * 删除（软删除进回收站）。
   *
   * @param path 相对路径
   * @param recursive 是否递归删除目录
   * @return 空结果
   */
  @DeleteMapping
  public Result<Void> delete(
      @RequestParam @NotBlank String path,
      @RequestParam(defaultValue = "false") boolean recursive) {
    fileService.delete(path, recursive);
    return Result.ok();
  }

  /**
   * 批量删除（软删除进回收站）。
   *
   * @param request 批量删除请求体（路径列表 + 是否递归）
   * @return 空结果
   */
  @PostMapping("/batch-delete")
  public Result<Void> deleteBatch(@Valid @RequestBody FileBatchDeleteRequest request) {
    fileService.deleteBatch(request.paths(), request.recursive());
    return Result.ok();
  }

  /**
   * 上传（multipart，≤1G 流式写盘 + 原子改名）。
   *
   * @param path 目标目录相对路径
   * @param file 上传文件
   * @return 上传后的文件条目
   */
  @PostMapping("/upload")
  public Result<FileEntry> upload(
      @RequestParam(defaultValue = "") String path, @RequestParam("file") MultipartFile file) {
    return Result.ok(fileService.upload(path, file));
  }

  /**
   * 下载（附件模式，支持 HTTP Range → 206）。
   *
   * @param path 相对路径
   * @param request 原始请求（解析 Range 头）
   * @return 文件响应（200 或 206 分段）
   * @throws IOException 文件读取失败时抛出
   */
  @GetMapping("/download")
  public ResponseEntity<?> download(@RequestParam @NotBlank String path, HttpServletRequest request)
      throws IOException {
    return fileStreamResponder.respond(fileService.stream(path), request, "attachment");
  }

  /**
   * 预览/流媒体（内联模式，支持 HTTP Range → 206；视频播放必需）。
   *
   * @param path 相对路径
   * @param request 原始请求（解析 Range 头）
   * @return 文件响应（200 或 206 分段）
   * @throws IOException 文件读取失败时抛出
   */
  @GetMapping("/stream")
  public ResponseEntity<?> stream(@RequestParam @NotBlank String path, HttpServletRequest request)
      throws IOException {
    return fileStreamResponder.respond(fileService.stream(path), request, "inline");
  }

  /**
   * 读取文本内容（md/txt 预览）。
   *
   * @param path 相对路径
   * @return 文本内容
   */
  @GetMapping("/text")
  public Result<String> text(@RequestParam @NotBlank String path) {
    return Result.ok(fileService.readText(path));
  }

  /**
   * 保存文本内容（md/txt 编辑）。
   *
   * @param request 写入请求体（路径 + 内容）
   * @return 空结果
   */
  @PutMapping("/write")
  public Result<Void> write(@Valid @RequestBody FileWriteRequest request) {
    fileService.writeText(request.path(), request.content());
    return Result.ok();
  }

  /**
   * 图片缩略图（懒生成 + 磁盘缓存，返回 256px JPEG）。
   *
   * @param path 相对路径
   * @return 缩略图资源响应（30 天缓存）
   * @throws IOException 缩略图读取失败时抛出
   */
  @GetMapping("/thumbnail")
  public ResponseEntity<Resource> thumbnail(@RequestParam @NotBlank String path)
      throws IOException {
    var info = fileService.thumbnail(path);
    FileSystemResource resource = new FileSystemResource(info.path());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(info.contentType()))
        .contentLength(info.size())
        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
        .body(resource);
  }

  /**
   * 文件名/路径搜索（file_index 索引）。
   *
   * @param q 关键字
   * @return 匹配的文件条目列表
   */
  @GetMapping("/search")
  public Result<List<FileEntry>> search(@RequestParam(required = false) String q) {
    return Result.ok(fileService.search(q));
  }

  /**
   * 全文检索（匹配 md/txt 内容）。
   */
  @GetMapping("/search-content")
  public Result<List<FileEntry>> searchContent(@RequestParam(required = false) String q) {
    return Result.ok(fileService.searchContent(q));
  }

  /**
   * 批量打包下载（zip；多选文件/目录）。
   */
  @PostMapping("/zip")
  public ResponseEntity<?> zipDownload(@RequestBody List<String> paths, HttpServletRequest request)
      throws IOException {
    return fileStreamResponder.respond(fileService.zipDownload(paths), request, "attachment");
  }
}
