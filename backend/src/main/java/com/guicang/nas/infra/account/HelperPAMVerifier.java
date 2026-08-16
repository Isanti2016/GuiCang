package com.guicang.nas.infra.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 生产 PAM 校验实现：经 sudo 白名单调用宿主 guicang-helper verify，密码经 stdin 传入。
 *
 * <p>后端进程不跑 root；helper 未部署时 verify 返回失败，登录会报"认证服务未就绪"。
 */
@Component
@ConditionalOnProperty(
    name = "guicang.auth.verifier",
    havingValue = "helper",
    matchIfMissing = true)
public class HelperPAMVerifier implements PAMVerifier {

  private static final Logger log = LoggerFactory.getLogger(HelperPAMVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${guicang.helper.path:/usr/local/bin/guicang-helper}")
  private String helperPath;

  @Override
  public PAMVerifyResult verify(String username, String password) {
    try {
      Process process = new ProcessBuilder("sudo", "-n", helperPath, "verify", username).start();
      // 密码仅经 stdin，不进入 argv
      process.getOutputStream().write((password + "\n").getBytes());
      process.getOutputStream().close();

      String output = new String(process.getInputStream().readAllBytes());
      process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

      JsonNode node = objectMapper.readTree(output);
      if (node.path("ok").asBoolean(false)) {
        return PAMVerifyResult.success(
            node.path("uid").asLong(),
            node.path("gid").asLong(),
            node.path("home").asText(),
            node.path("shell").asText());
      }
      return PAMVerifyResult.failure(node.path("error").asText("用户名或密码错误"));
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("helper 校验调用失败（helper 未部署或 sudo 白名单未配置）: user={}", username);
      return PAMVerifyResult.failure("认证服务未就绪");
    } catch (Exception e) {
      log.warn("helper 校验输出解析失败: user={}", username);
      return PAMVerifyResult.failure("认证服务异常");
    }
  }
}
