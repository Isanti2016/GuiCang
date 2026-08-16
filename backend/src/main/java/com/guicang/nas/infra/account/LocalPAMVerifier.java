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
 * 开发模式 PAM 校验实现：直接执行仓库内 guicang-pam-verify.py（无需 sudo/helper）。
 *
 * <p>仅本机开发可用（需要访问宿主机 /etc/shadow 与 libpam）；生产必须用 helper 实现。 配置：guicang.auth.verifier=local
 */
@Component
@ConditionalOnProperty(name = "guicang.auth.verifier", havingValue = "local")
public class LocalPAMVerifier implements PAMVerifier {

  private static final Logger log = LoggerFactory.getLogger(LocalPAMVerifier.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${guicang.helper.pam-script:../scripts/guicang-pam-verify.py}")
  private String pamScript;

  @Override
  public PAMVerifyResult verify(String username, String password) {
    try {
      Process process = new ProcessBuilder("python3", pamScript, username).start();
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
      log.warn("本地 PAM 校验调用失败: user={}", username);
      return PAMVerifyResult.failure("认证服务未就绪");
    } catch (Exception e) {
      log.warn("本地 PAM 校验输出解析失败: user={}", username);
      return PAMVerifyResult.failure("认证服务异常");
    }
  }
}
