package com.guicang.nas.module.file;

import com.guicang.nas.common.Result;
import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.module.file.dto.FileMoveRequest;
import com.guicang.nas.module.file.dto.FilePathRequest;
import com.guicang.nas.module.file.dto.FileRenameRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 文件管理接口（目录操作；路径为存储根下相对路径）。 */
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
}
