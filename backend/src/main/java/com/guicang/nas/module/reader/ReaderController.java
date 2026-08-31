package com.guicang.nas.module.reader;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.reader.dto.ReaderProgressVO;
import com.guicang.nas.module.reader.dto.SaveProgressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 小说阅读接口：打开书籍 / 读章节 / 进度记忆（需登录，路径级 READ 权限）。 */
@RestController
@RequestMapping("/api/v1/reader")
@Validated
public class ReaderController {

  private final ReaderService readerService;

  public ReaderController(ReaderService readerService) {
    this.readerService = readerService;
  }

  /** 打开书籍：元数据 + 章节列表。 */
  @GetMapping("/novel")
  public Result<NovelMeta> novel(@RequestParam @NotBlank String path) {
    return Result.ok(readerService.open(path));
  }

  /** 读取章节正文。 */
  @GetMapping("/chapter")
  public Result<ChapterContent> chapter(
      @RequestParam @NotBlank String path, @RequestParam @Min(0) int index) {
    return Result.ok(readerService.chapter(path, index));
  }

  /** 查询阅读进度。 */
  @GetMapping("/progress")
  public Result<ReaderProgressVO> progress(@RequestParam @NotBlank String path) {
    return Result.ok(readerService.progress(path));
  }

  /** 保存阅读进度。 */
  @PutMapping("/progress")
  public Result<Void> saveProgress(@Valid @RequestBody SaveProgressRequest request) {
    readerService.saveProgress(request);
    return Result.ok();
  }
}
