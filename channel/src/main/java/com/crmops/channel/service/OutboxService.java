package com.crmops.channel.service;

import com.crmops.channel.domain.EmailOutbox;
import com.crmops.channel.domain.OutboxStatus;
import com.crmops.channel.repo.EmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Local email mock: all outbound email lands in an outbox table and is
 * "sent" by the batch tier. Nothing here talks to an SMTP server.
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final EmailOutboxRepository repo;

    public OutboxService(EmailOutboxRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public EmailOutbox enqueue(String toAddress, Long toUserId, String subject, String body, String template) {
        log.info("queued {} message for {} (subject: {})", template, toAddress, subject);
        return repo.save(new EmailOutbox(toAddress, toUserId, subject, body, template, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<EmailOutbox> recent() {
        return repo.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<EmailOutbox> pending() {
        return repo.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    }
}
