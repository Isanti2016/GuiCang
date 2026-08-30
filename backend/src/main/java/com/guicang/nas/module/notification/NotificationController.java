package com.guicang.nas.module.notification;

import com.guicang.nas.common.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 站内通知接口。 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public Result<List<Notification>> list() {
    return Result.ok(notificationService.listForCurrentUser());
  }

  @GetMapping("/unread-count")
  public Result<Long> unreadCount() {
    return Result.ok(notificationService.unreadCount());
  }

  @PostMapping("/{id}/read")
  public Result<Void> markRead(@PathVariable Long id) {
    notificationService.markRead(id);
    return Result.ok();
  }

  @PostMapping("/read-all")
  public Result<Void> markAllRead() {
    notificationService.markAllRead();
    return Result.ok();
  }
}
