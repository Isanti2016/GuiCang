package com.guicang.nas.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.config.SecurityConfig;
import com.guicang.nas.module.auth.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 全局异常处理行为测试：所有异常统一返回 {@link Result} 结构。
 *
 * <p>本切片只测异常处理，排除 Security 相关组件（其依赖不属于 WebMvc 切片）。
 */
@WebMvcTest(
    controllers = TestExceptionController.class,
    excludeAutoConfiguration = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class
    },
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void 业务异常返回业务码与提示() throws Exception {
    mockMvc
        .perform(get("/test/biz"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(1001))
        .andExpect(jsonPath("$.message").value("业务失败示例"));
  }

  @Test
  void 参数校验失败返回400统一结构() throws Exception {
    mockMvc
        .perform(
            post("/test/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TestExceptionController.TestBody(""))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message").value("name 不能为空"));
  }

  @Test
  void 未知路径返回404统一结构() throws Exception {
    mockMvc
        .perform(get("/no/such/path"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("资源不存在"));
  }

  @Test
  void 未预期异常返回500且不泄露内部细节() throws Exception {
    mockMvc
        .perform(get("/test/runtime"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
  }
}
