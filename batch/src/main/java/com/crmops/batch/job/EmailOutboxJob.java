package com.crmops.batch.job;

import com.crmops.batch.repo.EmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Email outbox processor: the local mock that "sends" queued email. Real
 * deployments would replace the body of this job with an SMTP relay call.
 */
@Component
public class EmailOutboxJob {

    private static final Logger log = LoggerFactory.getLogger(EmailOutboxJob.class);

    private final EmailOutboxRepository outboxRepo;

    public EmailOutboxJob(EmailOutboxRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    @Transactional
    public int run() {
        int sent = 0;
        for (var email : outboxRepo.findTop20ByStatusOrderByCreatedAtAsc("PENDING")) {
            email.markSent(Instant.now());
            sent++;
        }
        if (sent > 0) {
            log.info("email outbox: marked {} messages sent (mock SMTP)", sent);
        }
        return sent;
    }
}
