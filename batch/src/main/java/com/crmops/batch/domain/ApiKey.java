package com.crmops.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "key_last4", nullable = false)
    private String keyLast4;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ApiKey() {
    }

    public static ApiKey create(long orgId, String name, String status, Instant expiresAt) {
        ApiKey key = new ApiKey();
        key.orgId = orgId;
        key.keyHash = "test-hash";
        key.keyPrefix = "crm_live";
        key.keyLast4 = "abcd";
        key.name = name;
        key.status = status;
        key.expiresAt = expiresAt;
        key.createdAt = Instant.now();
        return key;
    }

    public Long getId() {
        return id;
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

    public void expire() {
        this.status = "EXPIRED";
    }
}
