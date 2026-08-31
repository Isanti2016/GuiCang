package com.guicang.nas.module.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.guicang.nas.common.security.AuthenticatedUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 站内通知服务测试：创建、全局/个人可见性、未读数、已读、去重查询。 */
class NotificationServiceImplTest {

  private NotificationMapper mapper;
  private NotificationServiceImpl service;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(NotificationMapper.class);
    service = new NotificationServiceImpl(mapper);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("admin", 1003L), null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createInsertsUnread() {
    service.create("disk", "磁盘告警", "空间不足", null);
    verify(mapper).insert(any(Notification.class));
  }

  @Test
  void listIncludesGlobalAndPersonal() {
    Notification global = new Notification();
    global.setUsername(null);
    Notification mine = new Notification();
    mine.setUsername("admin");
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(global, mine));

    assertEquals(2, service.listForCurrentUser().size());
  }

  @Test
  void unreadCount() {
    when(mapper.selectCount(any(Wrapper.class))).thenReturn(3L);
    assertEquals(3, service.unreadCount());
  }

  @Test
  void markReadUnreadOnly() {
    Notification n = new Notification();
    n.setId(1L);
    n.setReadFlag(0);
    when(mapper.selectById(1L)).thenReturn(n);

    service.markRead(1L);

    assertEquals(1, n.getReadFlag());
    verify(mapper).updateById(n);
  }

  @Test
  void markReadAlreadyReadNoOp() {
    Notification n = new Notification();
    n.setId(1L);
    n.setReadFlag(1);
    when(mapper.selectById(1L)).thenReturn(n);

    service.markRead(1L);

    verify(mapper, never()).updateById(any(Notification.class));
  }

  @Test
  void markAllRead() {
    Notification a = new Notification();
    a.setReadFlag(0);
    Notification b = new Notification();
    b.setReadFlag(0);
    when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b));

    service.markAllRead();

    verify(mapper, Mockito.times(2)).updateById(any(Notification.class));
  }

  @Test
  void hasRecentQueriesUnreadSince() {
    when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
    assertEquals(true, service.hasRecent("disk", 24L * 3600 * 1000));
    when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    assertEquals(false, service.hasRecent("disk", 24L * 3600 * 1000));
  }
}
