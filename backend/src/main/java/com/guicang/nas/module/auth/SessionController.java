package com.guicang.nas.module.auth;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.auth.dto.SessionVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录会话管理接口。 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

  private final SessionService sessionService;

  public SessionController(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @GetMapping
  public Result<List<SessionVO>> list() {
    return Result.ok(sessionService.listForCurrentUser());
  }

  @PostMapping("/{id}/revoke")
  public Result<Void> revoke(@PathVariable Long id) {
    sessionService.revoke(id);
    return Result.ok();
  }
}
