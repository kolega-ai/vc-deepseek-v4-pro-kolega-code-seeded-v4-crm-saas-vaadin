package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_configs")
public class WebhookConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(nullable = false)
    private String url;

    private String description;

    /** Comma-separated event names this config subscribes to. */
    @Column(nullable = false)
    private String events;

    /**
     * Shared secret used to sign outbound deliveries. Stored locally so the
     * mock can actually sign payloads; masked in every UI and never returned
     * by the API after creation.
     */
    @Column(nullable = false)
    private String secret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebhookConfigStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookConfig() {
    }

    public WebhookConfig(Organization org, String url, String description, String events, String secret,
                         WebhookConfigStatus status, Instant now) {
        this.org = org;
        this.url = url;
        this.description = description;
        this.events = events;
        this.secret = secret;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrg() {
        return org;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getEvents() {
        return events;
    }

    public String getSecret() {
        return secret;
    }

    public WebhookConfigStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String url, String description, String events, WebhookConfigStatus status, Instant now) {
        this.url = url;
        this.description = description;
        this.events = events;
        this.status = status;
        this.updatedAt = now;
    }

    public boolean subscribesTo(String eventType) {
        for (String event : events.split(",")) {
            if (event.trim().equals(eventType)) {
                return true;
            }
        }
        return false;
    }
}
