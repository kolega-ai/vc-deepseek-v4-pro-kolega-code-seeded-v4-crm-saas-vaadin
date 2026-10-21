package com.crmops.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_key_records")
public class ApiKeyRecord {

    @Id
    @Column(name = "key_hash")
    private String keyHash;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ApiKeyRecord() {
    }

    public ApiKeyRecord(String keyHash, Long orgId, String name, String status, Instant expiresAt) {
        this.keyHash = keyHash;
        this.orgId = orgId;
        this.name = name;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void applySync(String name, String status, Instant expiresAt) {
        this.name = name;
        this.status = status;
        this.expiresAt = expiresAt;
    }
}
