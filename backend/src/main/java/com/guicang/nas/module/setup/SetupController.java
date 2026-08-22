package com.guicang.nas.module.setup;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.setup.dto.SetupInitRequest;
import com.guicang.nas.module.setup.dto.SetupStatusResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首次初始化向导接口。 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

  private final SetupService setupService;

  public SetupController(SetupService setupService) {
    this.setupService = setupService;
  }

  /**
   * 是否已初始化（前端据此跳转 /setup 向导）。
   *
   * @return 初始化状态
   */
  @GetMapping("/status")
  public Result<SetupStatusResponse> status() {
    return Result.ok(setupService.status());
  }

  /**
   * 执行初始化，完成后锁定。
   *
   * @param request 初始化请求体
   * @return 初始化后的状态
   */
  @PostMapping("/init")
  public Result<SetupStatusResponse> init(@Valid @RequestBody SetupInitRequest request) {
    return Result.ok(setupService.init(request));
  }
}
