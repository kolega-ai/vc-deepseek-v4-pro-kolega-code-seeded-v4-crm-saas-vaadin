package com.crmops.channel.repo;

import com.crmops.channel.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    List<Notification> findTop15ByUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long userId);
}
