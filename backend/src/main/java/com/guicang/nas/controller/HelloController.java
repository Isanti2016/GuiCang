package com.guicang.nas.controller;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 临时演示接口，验证工程可启动；Step 1.1 统一返回体后由模块化 Controller 取代。 */
@RestController
@RequestMapping("/api/v1")
public class HelloController {

  @GetMapping("/hello")
  public Map<String, String> hello() {
    return Map.of("service", "guicang-backend", "time", LocalDateTime.now().toString());
  }
}
