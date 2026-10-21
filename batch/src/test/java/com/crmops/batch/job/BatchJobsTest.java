package com.crmops.batch.job;

import com.crmops.batch.domain.AuditEvent;
import com.crmops.batch.domain.EmailOutbox;
import com.crmops.batch.domain.Escalation;
import com.crmops.batch.domain.Notification;
import com.crmops.batch.repo.ApiKeyRepository;
import com.crmops.batch.repo.AuditEventRepository;
import com.crmops.batch.repo.EmailOutboxRepository;
import com.crmops.batch.repo.EscalationRepository;
import com.crmops.batch.repo.NotificationRepository;
import com.crmops.batch.repo.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:batchtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false"})
class BatchJobsTest {

    @Autowired
    private EscalationSlaJob slaJob;
    @Autowired
    private ApiKeyExpiryJob expiryJob;
    @Autowired
    private EmailOutboxJob outboxJob;
    @Autowired
    private EscalationRepository escalationRepo;
    @Autowired
    private UserAccountRepository userRepo;
    @Autowired
    private NotificationRepository notificationRepo;
    @Autowired
    private AuditEventRepository auditRepo;
    @Autowired
    private ApiKeyRepository apiKeyRepo;
    @Autowired
    private EmailOutboxRepository outboxRepo;

    @Test
    @Transactional
    void slaJobFlagsOverdueEscalationsAndNotifies() {
        Escalation overdue = Escalation.create(1L, "Overdue", "SEV1", "OPEN", null,
                Instant.now().minus(10, java.time.temporal.ChronoUnit.HOURS),
                Instant.now().minus(5, java.time.temporal.ChronoUnit.HOURS));
        escalationRepo.save(overdue);
        Escalation fresh = Escalation.create(1L, "Fresh", "SEV1", "OPEN", null,
                Instant.now().minus(10, java.time.temporal.ChronoUnit.HOURS),
                Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS));
        escalationRepo.save(fresh);

        int flagged = slaJob.run();
        assertEquals(1, flagged);
        Escalation reloaded = escalationRepo.findById(overdue.getId()).orElseThrow();
        assertTrue(reloaded.isSlaBreached(), "overdue escalation must be flagged");
        assertEquals(0, notificationRepo.count(), "no assignee -> no direct notification");
        assertTrue(auditRepo.count() > 0, "SLA breach is audited");
        assertTrue(auditRepo.findAll().stream().anyMatch(a -> "escalation.sla_breached".equals(a.getAction())));
    }

    @Test
    @Transactional
    void apiKeyExpiryJobExpiresOnlyDueKeys() {
        com.crmops.batch.domain.ApiKey due = com.crmops.batch.domain.ApiKey.create(1L, "due",
                "ACTIVE", Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));
        apiKeyRepo.save(due);
        com.crmops.batch.domain.ApiKey fine = com.crmops.batch.domain.ApiKey.create(1L, "fine",
                "ACTIVE", Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS));
        apiKeyRepo.save(fine);

        int expired = expiryJob.run();
        assertEquals(1, expired);
        assertEquals("EXPIRED", apiKeyRepo.findById(due.getId()).orElseThrow().getStatus());
        assertEquals("ACTIVE", apiKeyRepo.findById(fine.getId()).orElseThrow().getStatus());
        assertTrue(auditRepo.findAll().stream().anyMatch(a -> "api_key.expire".equals(a.getAction())));
    }

    @Test
    @Transactional
    void outboxJobMarksPendingEmailsSent() {
        EmailOutbox email = EmailOutbox.create("someone@example.com", "subject", "body", "test",
                "PENDING", Instant.now());
        outboxRepo.save(email);
        assertEquals(1, outboxJob.run());
        assertEquals("SENT", outboxRepo.findById(email.getId()).orElseThrow().getStatus());
    }
}
