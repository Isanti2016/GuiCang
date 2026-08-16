package com.guicang.nas.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 仅供全局异常处理测试使用的测试控制器（仅存在于 test 源集）。 */
@RestController
public class TestExceptionController {

  @GetMapping("/test/biz")
  public Result<String> biz() {
    throw new BizException(1001, "业务失败示例");
  }

  @GetMapping("/test/runtime")
  public Result<String> runtime() {
    throw new IllegalStateException("内部错误");
  }

  @PostMapping("/test/valid")
  public Result<String> valid(@Valid @RequestBody TestBody body) {
    return Result.ok(body.name());
  }

  public record TestBody(@NotBlank(message = "不能为空") String name) {}
}
