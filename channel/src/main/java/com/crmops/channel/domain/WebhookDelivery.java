package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_summary")
    private String responseSummary;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(Long configId, Long orgId, String eventType, String url, String requestBody, Instant now) {
        this.configId = configId;
        this.orgId = orgId;
        this.eventType = eventType;
        this.url = url;
        this.requestBody = requestBody;
        this.status = DeliveryStatus.PENDING;
        this.attempt = 0;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getConfigId() {
        return configId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getUrl() {
        return url;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseSummary() {
        return responseSummary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void recordAttempt(int attemptNumber, DeliveryStatus newStatus, Integer responseStatus,
                              String responseSummary, String errorMessage, Instant now) {
        this.attempt = attemptNumber;
        this.status = newStatus;
        this.responseStatus = responseStatus;
        this.responseSummary = responseSummary;
        this.errorMessage = errorMessage;
        this.completedAt = now;
    }
}
