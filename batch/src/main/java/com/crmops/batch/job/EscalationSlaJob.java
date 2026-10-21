package com.crmops.batch.job;

import com.crmops.batch.domain.AuditEvent;
import com.crmops.batch.domain.Escalation;
import com.crmops.batch.domain.Notification;
import com.crmops.batch.repo.AuditEventRepository;
import com.crmops.batch.repo.EscalationRepository;
import com.crmops.batch.repo.NotificationRepository;
import com.crmops.batch.repo.UserAccountRepository;
import com.crmops.common.core.SlaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * SLA watchdog: flags escalations whose response deadline has passed, notifies
 * the assignee (or the whole support team) and writes the audit event.
 * Thresholds come from {@link SlaPolicy} — never from client input.
 */
@Component
public class EscalationSlaJob {

    private static final Logger log = LoggerFactory.getLogger(EscalationSlaJob.class);

    private final EscalationRepository escalationRepo;
    private final UserAccountRepository userRepo;
    private final NotificationRepository notificationRepo;
    private final AuditEventRepository auditRepo;

    public EscalationSlaJob(EscalationRepository escalationRepo, UserAccountRepository userRepo,
                            NotificationRepository notificationRepo, AuditEventRepository auditRepo) {
        this.escalationRepo = escalationRepo;
        this.userRepo = userRepo;
        this.notificationRepo = notificationRepo;
        this.auditRepo = auditRepo;
    }

    @Scheduled(fixedDelay = 5 * 60_000, initialDelay = 45_000)
    @Transactional
    public int run() {
        Instant now = Instant.now();
        List<Escalation> breached = escalationRepo
                .findByStatusInAndSlaBreachedFalseAndSlaDueAtBefore(
                        List.of("OPEN", "WAITING_ON_INTERNAL"), now);
        for (Escalation escalation : breached) {
            escalation.flagSlaBreached(now);
            auditRepo.save(AuditEvent.system("escalation.sla_breached", "escalation", escalation.getId(),
                    escalation.getOrgId(), "SYSTEM:batch:escalation-sla", false));
            if (escalation.getAssigneeId() != null) {
                notificationRepo.save(Notification.of(escalation.getAssigneeId(), "escalation",
                        "SLA breached: " + escalation.getSubject(),
                        "This escalation passed its response deadline (" + SlaPolicy.threshold(escalation.getSeverity()).toHours()
                                + "h for " + escalation.getSeverity() + ").",
                        "escalations/" + escalation.getId(), now));
            } else {
                for (var agent : userRepo.findByRoleAndEnabledTrue("SUPPORT_AGENT")) {
                    notificationRepo.save(Notification.of(agent.getId(), "escalation",
                            "SLA breached (unassigned): " + escalation.getSubject(),
                            "An unassigned escalation passed its response deadline.",
                            "escalations/" + escalation.getId(), now));
                }
            }
        }
        if (!breached.isEmpty()) {
            log.info("flagged {} escalations as SLA-breached", breached.size());
        }
        return breached.size();
    }
}
