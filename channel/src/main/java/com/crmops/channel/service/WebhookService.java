package com.crmops.channel.service;

import com.crmops.channel.config.ChannelProperties;
import com.crmops.channel.domain.DeliveryStatus;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.WebhookConfig;
import com.crmops.channel.domain.WebhookConfigStatus;
import com.crmops.channel.domain.WebhookDelivery;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.repo.WebhookConfigRepository;
import com.crmops.channel.repo.WebhookDeliveryRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.crmops.common.core.Actions.WEBHOOK_MANAGE;
import static com.crmops.common.core.Actions.WEBHOOK_READ;
import static com.crmops.common.core.Actions.WEBHOOK_TEST;

/**
 * Outbound webhook configuration and delivery. Deliveries are signed with the
 * configured secret (HMAC-SHA256) and recorded with attempt, result, timestamp
 * and response summary. Real event deliveries are enqueued here and processed
 * by the batch tier; test deliveries run synchronously so the UI can show the
 * result immediately. The secret is stored so payloads can be signed locally;
 * it is masked in every UI and never returned after creation.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public static final Set<String> KNOWN_EVENTS = Set.of(
            "escalation.created", "escalation.updated", "escalation.resolved",
            "document.uploaded", "deal.stage_changed", "renewal.due");

    private final WebhookConfigRepository configRepo;
    private final WebhookDeliveryRepository deliveryRepo;
    private final OrganizationRepository orgRepo;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final int timeoutMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookService(WebhookConfigRepository configRepo, WebhookDeliveryRepository deliveryRepo,
                          OrganizationRepository orgRepo, CrmAuthorization authz, AuditService audit,
                          ObjectMapper mapper, ChannelProperties properties) {
        this.configRepo = configRepo;
        this.deliveryRepo = deliveryRepo;
        this.orgRepo = orgRepo;
        this.authz = authz;
        this.audit = audit;
        this.mapper = mapper;
        this.timeoutMs = properties.webhookTimeoutMs();
    }

    @Transactional(readOnly = true)
    public List<WebhookConfig> listConfigs(long orgId) {
        authz.require(WEBHOOK_READ, orgId);
        return configRepo.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional
    public WebhookConfig createConfig(long orgId, String url, String description, List<String> events) {
        var verdict = authz.require(WEBHOOK_MANAGE, orgId);
        validate(url, events);
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        WebhookConfig config = new WebhookConfig(org, url.trim(), description,
                String.join(",", events.stream().sorted().toList()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(secret),
                WebhookConfigStatus.ACTIVE, Instant.now());
        configRepo.save(config);
        audit.record("webhook.create", "webhook_config", config.getId(), orgId, null,
                Map.of("url", config.getUrl(), "events", config.getEvents()),
                verdict.authorizationBasis(), false);
        return config;
    }

    @Transactional
    public WebhookConfig updateConfig(long configId, String url, String description, List<String> events,
                                      boolean active) {
        WebhookConfig config = configRepo.findById(configId)
                .orElseThrow(() -> new NotFoundException("webhook config", configId));
        var verdict = authz.require(WEBHOOK_MANAGE, config.getOrg().getId());
        validate(url, events);
        config.update(url.trim(), description, String.join(",", events.stream().sorted().toList()),
                active ? WebhookConfigStatus.ACTIVE : WebhookConfigStatus.DISABLED, Instant.now());
        audit.record("webhook.update", "webhook_config", configId, config.getOrg().getId(), null,
                Map.of("url", config.getUrl(), "events", config.getEvents()),
                verdict.authorizationBasis(), false);
        return config;
    }

    @Transactional
    public void deleteConfig(long configId) {
        WebhookConfig config = configRepo.findById(configId)
                .orElseThrow(() -> new NotFoundException("webhook config", configId));
        var verdict = authz.require(WEBHOOK_MANAGE, config.getOrg().getId());
        configRepo.delete(config);
        audit.record("webhook.delete", "webhook_config", configId, config.getOrg().getId(),
                Map.of("url", config.getUrl()), null, verdict.authorizationBasis(), false);
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> deliveryHistory(long configId) {
        WebhookConfig config = configRepo.findById(configId)
                .orElseThrow(() -> new NotFoundException("webhook config", configId));
        authz.require(WEBHOOK_READ, config.getOrg().getId());
        return deliveryRepo.findTop50ByConfigIdOrderByCreatedAtDesc(configId);
    }

    /**
     * Enqueues a delivery for every active config of the org subscribed to the
     * event. Called by workflow services on domain events.
     */
    @Transactional
    public void enqueueForOrg(long orgId, String eventType, Map<String, Object> payload) {
        List<WebhookConfig> configs = configRepo.findByOrgIdOrderByCreatedAtDesc(orgId);
        for (WebhookConfig config : configs) {
            if (config.getStatus() == WebhookConfigStatus.ACTIVE && config.subscribesTo(eventType)) {
                deliveryRepo.save(new WebhookDelivery(config.getId(), orgId, eventType, config.getUrl(),
                        toJson(payload), Instant.now()));
            }
        }
    }

    /**
     * Checks that an endpoint answers before the configuration is saved, so a
     * typo in the dialog is caught while the operator is still looking at it.
     */
    public String probeEndpoint(String endpoint) {
        authz.require(WEBHOOK_TEST);
        if (endpoint == null || endpoint.isBlank()) {
            throw new ValidationException("enter the endpoint URL first");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.trim()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-Crmops-Event", "endpoint.check")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"event\":\"endpoint.check\"}",
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return "HTTP " + response.statusCode() + " · " + truncate(response.body(), 200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "no answer: interrupted";
        } catch (Exception e) {
            return "no answer: " + e.getMessage();
        }
    }

    /** Synchronous test delivery: attempts the request and records the result. */
    @Transactional
    public WebhookDelivery testDelivery(long configId, String eventType) {
        WebhookConfig config = configRepo.findById(configId)
                .orElseThrow(() -> new NotFoundException("webhook config", configId));
        var verdict = authz.require(WEBHOOK_TEST, config.getOrg().getId());
        Map<String, Object> payload = Map.of(
                "event", eventType,
                "test", true,
                "orgId", config.getOrg().getId(),
                "timestamp", Instant.now().toString());
        WebhookDelivery delivery = new WebhookDelivery(config.getId(), config.getOrg().getId(), eventType,
                config.getUrl(), toJson(payload), Instant.now());
        attempt(config, delivery, 1);
        deliveryRepo.save(delivery);
        audit.record("webhook.test_delivery", "webhook_delivery", delivery.getId(), config.getOrg().getId(),
                null, Map.of("event", eventType, "status", delivery.getStatus().name()),
                verdict.authorizationBasis(), false);
        return delivery;
    }

    /** Performs one HTTP delivery attempt (used by tests and the batch tier's retry logic). */
    @Transactional
    public void attempt(WebhookConfig config, WebhookDelivery delivery, int attemptNumber) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-Crmops-Event", delivery.getEventType())
                    .header("X-Crmops-Delivery", String.valueOf(delivery.getId()))
                    .header("X-Crmops-Signature", "sha256=" + hmac(config.getSecret(), delivery.getRequestBody()))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getRequestBody(), StandardCharsets.UTF_8));
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String summary = response.body() == null ? "" : response.body();
            if (summary.length() > 400) {
                summary = summary.substring(0, 400) + "…";
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.recordAttempt(attemptNumber, DeliveryStatus.SUCCESS, response.statusCode(), summary, null,
                        Instant.now());
            } else {
                delivery.recordAttempt(attemptNumber, DeliveryStatus.FAILED, response.statusCode(), summary,
                        "unexpected status " + response.statusCode(), Instant.now());
            }
        } catch (Exception e) {
            log.info("webhook attempt {} for delivery {} failed: {}", attemptNumber, delivery.getId(), e.getMessage());
            delivery.recordAttempt(attemptNumber, DeliveryStatus.FAILED, null, null,
                    truncate(e.getMessage(), 500), Instant.now());
        }
    }

    public static String hmac(String secret, String body) {
        return com.crmops.common.core.WebhookSigner.hmacSha256(secret, body);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static void validate(String url, List<String> events) {
        if (url == null || url.isBlank() || url.length() > 500
                || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new ValidationException("a valid http(s) URL is required");
        }
        if (events == null || events.isEmpty()) {
            throw new ValidationException("select at least one event");
        }
        for (String event : events) {
            if (!KNOWN_EVENTS.contains(event)) {
                throw new ValidationException("unknown event type: " + event);
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
