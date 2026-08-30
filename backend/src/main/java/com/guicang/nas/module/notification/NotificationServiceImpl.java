package com.guicang.nas.module.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.ResultCodes;
import com.guicang.nas.common.security.AuthenticatedUser;
import com.guicang.nas.common.security.CurrentUserContext;
import java.util.List;
import org.springframework.stereotype.Service;

/** 站内通知服务实现。 */
@Service
public class NotificationServiceImpl implements NotificationService {

  private final NotificationMapper notificationMapper;

  public NotificationServiceImpl(NotificationMapper notificationMapper) {
    this.notificationMapper = notificationMapper;
  }

  @Override
  public void create(String type, String title, String content, String username) {
    Notification n = new Notification();
    n.setType(type);
    n.setTitle(title);
    n.setContent(content);
    n.setUsername(username);
    n.setReadFlag(0);
    n.setCreatedAt(System.currentTimeMillis());
    notificationMapper.insert(n);
  }

  @Override
  public List<Notification> listForCurrentUser() {
    String username = requireUser().username();
    return notificationMapper.selectList(
        new LambdaQueryWrapper<Notification>()
            .and(w -> w.isNull(Notification::getUsername).or().eq(Notification::getUsername, username))
            .orderByDesc(Notification::getCreatedAt));
  }

  @Override
  public long unreadCount() {
    String username = requireUser().username();
    return notificationMapper.selectCount(
        new LambdaQueryWrapper<Notification>()
            .and(w -> w.isNull(Notification::getUsername).or().eq(Notification::getUsername, username))
            .eq(Notification::getReadFlag, 0));
  }

  @Override
  public void markRead(Long id) {
    Notification n = notificationMapper.selectById(id);
    if (n != null && n.getReadFlag() != null && n.getReadFlag() == 0) {
      n.setReadFlag(1);
      notificationMapper.updateById(n);
    }
  }

  @Override
  public void markAllRead() {
    String username = requireUser().username();
    List<Notification> list = notificationMapper.selectList(
        new LambdaQueryWrapper<Notification>()
            .and(w -> w.isNull(Notification::getUsername).or().eq(Notification::getUsername, username))
            .eq(Notification::getReadFlag, 0));
    for (Notification n : list) {
      n.setReadFlag(1);
      notificationMapper.updateById(n);
    }
  }

  @Override
  public boolean hasRecent(String type, long withinMillis) {
    return notificationMapper.selectCount(
        new LambdaQueryWrapper<Notification>()
            .eq(Notification::getType, type)
            .eq(Notification::getReadFlag, 0)
            .ge(Notification::getCreatedAt, System.currentTimeMillis() - withinMillis)) > 0;
  }

  private AuthenticatedUser requireUser() {
    return CurrentUserContext.currentUser()
        .orElseThrow(() -> new BizException(ResultCodes.UNAUTHORIZED, "未登录或登录已过期"));
  }
}
