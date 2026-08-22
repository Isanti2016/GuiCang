package com.guicang.nas.module.file;

import com.guicang.nas.common.Result;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 回收站接口（路径为存储根下相对路径）。 */
@RestController
@RequestMapping("/api/v1/files/trash")
public class TrashController {

  private final FileService fileService;

  public TrashController(FileService fileService) {
    this.fileService = fileService;
  }

  /**
   * 回收站列表（管理员全部，普通用户本人）。
   *
   * @return 回收站条目列表
   */
  @GetMapping
  public Result<List<TrashItem>> list() {
    return Result.ok(fileService.listTrash());
  }

  /**
   * 恢复到原路径。
   *
   * @param id 回收站条目 ID
   * @return 空结果
   */
  @PostMapping("/{id}/restore")
  public Result<Void> restore(@PathVariable Long id) {
    fileService.restoreTrash(id);
    return Result.ok();
  }

  /**
   * 彻底删除单个条目。
   *
   * @param id 回收站条目 ID
   * @return 空结果
   */
  @DeleteMapping("/{id}")
  public Result<Void> purge(@PathVariable Long id) {
    fileService.purgeTrash(id);
    return Result.ok();
  }

  /**
   * 清空回收站。
   *
   * @param all 是否同时清空所有用户的回收站
   * @return 空结果
   */
  @DeleteMapping
  public Result<Void> empty(@RequestParam(defaultValue = "false") boolean all) {
    fileService.emptyTrash();
    return Result.ok();
  }
}
