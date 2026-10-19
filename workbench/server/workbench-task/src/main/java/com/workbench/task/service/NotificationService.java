package com.workbench.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.api.BusinessException;
import com.workbench.task.entity.Notification;
import com.workbench.task.mapper.NotificationMapper;
import com.workbench.task.web.CommentDtos.NotificationView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 站内通知：列表（未读数）、标记已读。 */
@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public List<NotificationView> list(long currentUserid, boolean unreadOnly) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserid, currentUserid)
                .orderByDesc(Notification::getId);
        if (unreadOnly) {
            wrapper.isNull(Notification::getReadAt);
        }
        return notificationMapper.selectList(wrapper).stream()
                .map(NotificationService::toView)
                .toList();
    }

    public long unreadCount(long currentUserid) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserid, currentUserid)
                .isNull(Notification::getReadAt));
        return count == null ? 0 : count;
    }

    public void markRead(long currentUserid, long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !Objects.equals(notification.getUserid(), currentUserid)) {
            throw new BusinessException("通知不存在");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    public int markAllRead(long currentUserid) {
        List<Notification> unread = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserid, currentUserid)
                .isNull(Notification::getReadAt));
        LocalDateTime now = LocalDateTime.now();
        for (Notification notification : unread) {
            notification.setReadAt(now);
            notificationMapper.updateById(notification);
        }
        return unread.size();
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getPayload(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
