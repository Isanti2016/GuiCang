package com.guicang.nas.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

  /** 仅 POST：用于触发 405。 */
  @PostMapping("/test/post-only")
  public Result<String> postOnly() {
    return Result.ok("ok");
  }

  /** 必填查询参数：用于触发 MissingServletRequestParameterException。 */
  @GetMapping("/test/need-param")
  public Result<String> needParam(@RequestParam String name) {
    return Result.ok(name);
  }

  public record TestBody(@NotBlank(message = "不能为空") String name) {}
}
