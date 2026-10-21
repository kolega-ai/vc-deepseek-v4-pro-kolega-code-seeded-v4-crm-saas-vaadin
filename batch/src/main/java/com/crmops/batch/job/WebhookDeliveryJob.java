package com.crmops.batch.job;

import com.crmops.batch.config.BatchProperties;
import com.crmops.batch.domain.WebhookConfig;
import com.crmops.batch.domain.WebhookDelivery;
import com.crmops.batch.repo.WebhookConfigRepository;
import com.crmops.batch.repo.WebhookDeliveryRepository;
import com.crmops.common.core.WebhookSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Webhook delivery worker: picks up PENDING deliveries, signs the payload with
 * the config's secret (HMAC-SHA256) and records the attempt, result and
 * response summary. Failed deliveries are retried up to the configured limit
 * with the 30s job cadence acting as backoff.
 */
@Component
public class WebhookDeliveryJob {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryJob.class);

    private final WebhookDeliveryRepository deliveryRepo;
    private final WebhookConfigRepository configRepo;
    private final BatchProperties properties;

    public WebhookDeliveryJob(WebhookDeliveryRepository deliveryRepo, WebhookConfigRepository configRepo,
                              BatchProperties properties) {
        this.deliveryRepo = deliveryRepo;
        this.configRepo = configRepo;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    @Transactional
    public int run() {
        int processed = 0;
        for (WebhookDelivery delivery : deliveryRepo
                .findTop20ByStatusAndAttemptLessThanOrderByCreatedAtAsc("PENDING", properties.webhookMaxAttempts())) {
            WebhookConfig config = configRepo.findById(delivery.getConfigId()).orElse(null);
            if (config == null || !"ACTIVE".equals(config.getStatus())) {
                delivery.recordAttempt(delivery.getAttempt() + 1, "FAILED", null, null,
                        "webhook configuration missing or disabled", Instant.now());
                processed++;
                continue;
            }
            attempt(config, delivery);
            processed++;
        }
        return processed;
    }

    @Transactional
    public void attempt(WebhookConfig config, WebhookDelivery delivery) {
        int attemptNumber = delivery.getAttempt() + 1;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(properties.webhookTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("X-Crmops-Event", delivery.getEventType())
                    .header("X-Crmops-Delivery", String.valueOf(delivery.getId()))
                    .header("X-Crmops-Signature", "sha256="
                            + WebhookSigner.hmacSha256(config.getSecret(), delivery.getRequestBody()))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getRequestBody(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String summary = response.body() == null ? "" : response.body();
            if (summary.length() > 400) {
                summary = summary.substring(0, 400) + "…";
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.recordAttempt(attemptNumber, "SUCCESS", response.statusCode(), summary, null, Instant.now());
            } else {
                delivery.recordAttempt(attemptNumber, attemptNumber >= properties.webhookMaxAttempts() ? "FAILED" : "PENDING",
                        response.statusCode(), summary, "unexpected status " + response.statusCode(), Instant.now());
            }
        } catch (Exception e) {
            log.info("webhook delivery {} attempt {} failed: {}", delivery.getId(), attemptNumber, e.getMessage());
            delivery.recordAttempt(attemptNumber, attemptNumber >= properties.webhookMaxAttempts() ? "FAILED" : "PENDING",
                    null, null, truncate(e.getMessage(), 500), Instant.now());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
