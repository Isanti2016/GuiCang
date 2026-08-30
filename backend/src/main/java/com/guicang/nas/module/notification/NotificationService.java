package com.guicang.nas.module.notification;

import java.util.List;

/** 站内通知服务。 */
public interface NotificationService {

  /** 创建通知（username 为 null 表示广播）。 */
  void create(String type, String title, String content, String username);

  /** 当前用户可见的通知列表（倒序）。 */
  List<Notification> listForCurrentUser();

  /** 当前用户未读数。 */
  long unreadCount();

  /** 标记单条已读。 */
  void markRead(Long id);

  /** 当前用户全部已读。 */
  void markAllRead();

  /** 最近 withinMillis 内是否存在指定类型的未读通知（告警去重用）。 */
  boolean hasRecent(String type, long withinMillis);
}
