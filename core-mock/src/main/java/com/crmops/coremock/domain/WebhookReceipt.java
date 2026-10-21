package com.crmops.coremock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_receipts")
public class WebhookReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenant;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "payload_digest", nullable = false)
    private String payloadDigest;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookReceipt() {
    }

    public WebhookReceipt(String tenant, String eventType, String payloadDigest) {
        this.tenant = tenant;
        this.eventType = eventType;
        this.payloadDigest = payloadDigest;
        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTenant() {
        return tenant;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
