package com.crmops.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_configs")
public class WebhookConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String events;

    @Column(nullable = false)
    private String secret;

    @Column(nullable = false)
    private String status;

    protected WebhookConfig() {
    }

    public Long getId() {
        return id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getUrl() {
        return url;
    }

    public String getEvents() {
        return events;
    }

    public String getSecret() {
        return secret;
    }

    public String getStatus() {
        return status;
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
