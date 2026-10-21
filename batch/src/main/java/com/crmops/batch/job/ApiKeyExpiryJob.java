package com.crmops.batch.job;

import com.crmops.batch.domain.AuditEvent;
import com.crmops.batch.repo.ApiKeyRepository;
import com.crmops.batch.repo.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Expires API keys whose expiry date has passed, using the same explicit
 * ACTIVE → EXPIRED transition the channel tier enforces, and records an
 * append-only audit event with a SYSTEM basis.
 */
@Component
public class ApiKeyExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyExpiryJob.class);

    private final ApiKeyRepository keyRepo;
    private final AuditEventRepository auditRepo;

    public ApiKeyExpiryJob(ApiKeyRepository keyRepo, AuditEventRepository auditRepo) {
        this.keyRepo = keyRepo;
        this.auditRepo = auditRepo;
    }

    @Scheduled(cron = "0 0 * * * *") // hourly
    @Transactional
    public int run() {
        Instant now = Instant.now();
        int expired = 0;
        for (var key : keyRepo.findByStatusAndExpiresAtBefore("ACTIVE", now)) {
            key.expire();
            expired++;
            auditRepo.save(AuditEvent.system("api_key.expire", "api_key", key.getId(), key.getOrgId(),
                    "SYSTEM:batch:api-key-expiry", false));
        }
        if (expired > 0) {
            log.info("expired {} api keys", expired);
        }
        return expired;
    }
}
