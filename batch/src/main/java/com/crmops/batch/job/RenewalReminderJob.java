package com.crmops.batch.job;

import com.crmops.batch.config.BatchProperties;
import com.crmops.batch.domain.AuditEvent;
import com.crmops.batch.domain.Notification;
import com.crmops.batch.domain.WebhookDelivery;
import com.crmops.batch.repo.AuditEventRepository;
import com.crmops.batch.repo.NotificationRepository;
import com.crmops.batch.repo.OrganizationRepository;
import com.crmops.batch.repo.WebhookConfigRepository;
import com.crmops.batch.repo.WebhookDeliveryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Renewal reminders: notifies the owners of organizations renewing inside the
 * window (owner ids come from the DECISION tier — never from local guesswork),
 * enqueues renewal.due webhook deliveries and writes the audit events.
 */
@Component
public class RenewalReminderJob {

    private static final Logger log = LoggerFactory.getLogger(RenewalReminderJob.class);

    private final OrganizationRepository orgRepo;
    private final WebhookConfigRepository webhookConfigRepo;
    private final WebhookDeliveryRepository deliveryRepo;
    private final NotificationRepository notificationRepo;
    private final AuditEventRepository auditRepo;
    private final BatchProperties properties;
    private final RestClient decisionRest;
    private final ObjectMapper mapper = new ObjectMapper();

    public RenewalReminderJob(OrganizationRepository orgRepo, WebhookConfigRepository webhookConfigRepo,
                              WebhookDeliveryRepository deliveryRepo, NotificationRepository notificationRepo,
                              AuditEventRepository auditRepo, BatchProperties properties) {
        this.orgRepo = orgRepo;
        this.webhookConfigRepo = webhookConfigRepo;
        this.deliveryRepo = deliveryRepo;
        this.notificationRepo = notificationRepo;
        this.auditRepo = auditRepo;
        this.properties = properties;
        this.decisionRest = RestClient.builder().baseUrl(properties.decisionUrl()).build();
    }

    @Scheduled(cron = "0 0 2 * * *") // daily 02:00
    @Transactional
    public int run() {
        LocalDate today = LocalDate.now();
        int reminded = 0;
        var due = orgRepo.findByRenewalDateBetweenAndStatus(
                today, today.plusDays(properties.renewalWindowDays()), "ACTIVE");
        for (var org : due) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, org.getRenewalDate());
            String message = org.getName() + " renews in " + days + " days ("
                    + org.getRenewalDate() + ").";
            for (Long ownerId : ownersOf(org.getId())) {
                notificationRepo.save(Notification.of(ownerId, "renewal", "Renewal due soon: " + org.getName(),
                        message, "organizations/" + org.getId(), Instant.now()));
                reminded++;
            }
            enqueueRenewalWebhooks(org.getId(), org.getName(), days);
            auditRepo.save(AuditEvent.system("renewal.reminder_sent", "organization", org.getId(), org.getId(),
                    "SYSTEM:batch:renewal-reminder", false));
        }
        if (reminded > 0) {
            log.info("sent {} renewal reminders for {} organizations", reminded, due.size());
        }
        return reminded;
    }

    /** Owner ids come from the decision tier's assignment table via its service API. */
    private List<Long> ownersOf(long orgId) {
        try {
            return decisionRest.get()
                    .uri("/api/service/orgs/{orgId}/owners", orgId)
                    .header("X-Service-Token", properties.channelServiceToken())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<Long>>() {
                    });
        } catch (Exception e) {
            log.warn("could not resolve owners for org {} from decision: {}", orgId, e.getMessage());
            return List.of();
        }
    }

    private void enqueueRenewalWebhooks(long orgId, String orgName, long days) {
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of(
                    "event", "renewal.due", "orgId", orgId, "orgName", orgName,
                    "daysUntilRenewal", days, "timestamp", Instant.now().toString()));
        } catch (Exception e) {
            payload = "{}";
        }
        for (var config : webhookConfigRepo.findByStatus("ACTIVE")) {
            if (config.getOrgId().equals(orgId) && config.subscribesTo("renewal.due")) {
                deliveryRepo.save(WebhookDelivery.create(config.getId(), orgId, "renewal.due",
                        config.getUrl(), payload, Instant.now()));
            }
        }
    }
}
