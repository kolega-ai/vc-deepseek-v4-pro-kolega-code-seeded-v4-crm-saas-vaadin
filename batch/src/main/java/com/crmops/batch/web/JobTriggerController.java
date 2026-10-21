package com.crmops.batch.web;

import com.crmops.batch.job.ApiKeyExpiryJob;
import com.crmops.batch.job.EmailOutboxJob;
import com.crmops.batch.job.EscalationSlaJob;
import com.crmops.batch.job.RenewalReminderJob;
import com.crmops.batch.job.WebhookDeliveryJob;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Manual job triggers for the local demo (e.g. run renewal reminders on demand). */
@RestController
@RequestMapping("/api/jobs")
public class JobTriggerController {

    private final EmailOutboxJob outboxJob;
    private final WebhookDeliveryJob webhookJob;
    private final ApiKeyExpiryJob expiryJob;
    private final EscalationSlaJob slaJob;
    private final RenewalReminderJob renewalJob;

    public JobTriggerController(EmailOutboxJob outboxJob, WebhookDeliveryJob webhookJob,
                                ApiKeyExpiryJob expiryJob, EscalationSlaJob slaJob,
                                RenewalReminderJob renewalJob) {
        this.outboxJob = outboxJob;
        this.webhookJob = webhookJob;
        this.expiryJob = expiryJob;
        this.slaJob = slaJob;
        this.renewalJob = renewalJob;
    }

    @PostMapping("/{job}/run")
    public Map<String, Object> run(@PathVariable String job) {
        int processed = switch (job) {
            case "outbox" -> outboxJob.run();
            case "webhooks" -> webhookJob.run();
            case "api-key-expiry" -> expiryJob.run();
            case "escalation-sla" -> slaJob.run();
            case "renewal-reminders" -> renewalJob.run();
            default -> throw new IllegalArgumentException("unknown job: " + job);
        };
        return Map.of("job", job, "processed", processed);
    }
}
