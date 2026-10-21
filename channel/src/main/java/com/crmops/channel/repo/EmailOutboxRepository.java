package com.crmops.channel.repo;

import com.crmops.channel.domain.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    List<EmailOutbox> findTop50ByOrderByCreatedAtDesc();

    List<EmailOutbox> findTop20ByStatusOrderByCreatedAtAsc(com.crmops.channel.domain.OutboxStatus status);
}
