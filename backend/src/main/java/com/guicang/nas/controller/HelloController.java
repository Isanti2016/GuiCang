package com.guicang.nas.controller;

import com.guicang.nas.common.Result;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 临时演示接口，验证工程可启动；Step 1.1 后由模块化 Controller 取代。 */
@RestController
@RequestMapping("/api/v1")
public class HelloController {

  @GetMapping("/hello")
  public Result<Map<String, String>> hello() {
    return Result.ok(Map.of("service", "guicang-backend", "time", LocalDateTime.now().toString()));
  }
}
