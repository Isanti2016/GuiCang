package com.guicang.nas.module.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.module.audit.AuditLog;
import com.guicang.nas.module.audit.AuditLogMapper;
import com.guicang.nas.module.setup.dto.SetupInitRequest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 首次初始化流程测试：未初始化跳向导、初始化后锁定、配置落库、审计埋点。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetupFlowTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SysConfigMapper sysConfigMapper;

  @Autowired private AuditLogMapper auditLogMapper;

  @Test
  @Order(1)
  void 未初始化时状态返回false() throws Exception {
    mockMvc
        .perform(get("/api/v1/setup/status"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.SUCCESS))
        .andExpect(jsonPath("$.data.initialized").value(false));
  }

  @Test
  @Order(2)
  void 初始化成功锁定并落库且审计留痕() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/setup/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetupInitRequest("admin", "管理员"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.initialized").value(true));

    assertThat(sysConfigMapper.selectById("setup.done").getValue()).isEqualTo("1");
    assertThat(sysConfigMapper.selectById("setup.admin.username").getValue()).isEqualTo("admin");
    // helper 未接入（Step 2.1），管理员系统账号标记待创建
    assertThat(sysConfigMapper.selectById("setup.admin.pending").getValue()).isEqualTo("1");

    AuditLog audit =
        auditLogMapper.selectOne(
            new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getAction, "setup.init")
                .last("LIMIT 1"));
    assertThat(audit).isNotNull();
    // SpEL resource 解析：#request.username() -> admin
    assertThat(audit.getResource()).isEqualTo("admin");
    assertThat(audit.getResult()).isEqualTo("success");
  }

  @Test
  @Order(3)
  void 重复初始化被拒绝() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/setup/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetupInitRequest("admin2", "管理员2"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(ResultCodes.BIZ_ERROR))
        .andExpect(jsonPath("$.message").value("系统已完成初始化，不能重复初始化"));
  }

  @Test
  @Order(4)
  void 非法用户名被校验拒绝() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/setup/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetupInitRequest("Admin!", "管理员"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(ResultCodes.BAD_REQUEST));
  }
}
