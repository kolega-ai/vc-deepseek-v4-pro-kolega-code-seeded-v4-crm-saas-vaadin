package com.crmops.channel.service;

import com.crmops.channel.domain.Notification;
import com.crmops.channel.repo.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** In-app notifications for workflow events. */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void notify(Long userId, String type, String title, String message, String link) {
        repo.save(new Notification(userId, type, title, message, link, Instant.now()));
    }

    @Transactional
    public void notifyMany(List<Long> userIds, String type, String title, String message, String link) {
        Instant now = Instant.now();
        for (Long userId : userIds) {
            repo.save(new Notification(userId, type, title, message, link, now));
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> list(long userId) {
        return repo.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return repo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> unread(long userId) {
        return repo.findTop15ByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markRead(long notificationId, long userId) {
        repo.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> n.markRead(Instant.now()));
    }

    @Transactional
    public void markAllRead(long userId) {
        repo.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .forEach(n -> n.markRead(Instant.now()));
    }
}
