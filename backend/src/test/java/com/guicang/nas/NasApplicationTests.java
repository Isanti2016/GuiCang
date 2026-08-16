package com.guicang.nas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 骨架冒烟测试：验证 Spring 上下文可正常装配启动（测试 profile 使用内存 SQLite）。 */
@SpringBootTest
@ActiveProfiles("test")
class NasApplicationTests {

  @Test
  void contextLoads() {}
}
