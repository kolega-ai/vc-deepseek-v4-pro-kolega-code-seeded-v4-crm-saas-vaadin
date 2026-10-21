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

/**
 * Organization-scoped API key. Only the SHA-256 hash of the secret is stored —
 * the full value is shown exactly once at creation. Status transitions follow
 * the explicit edge list in {@code ApiKeyStateMachine}.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "key_last4", nullable = false)
    private String keyLast4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApiKeyStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "last_used_path")
    private String lastUsedPath;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "rotated_from_id")
    private Long rotatedFromId;

    protected ApiKey() {
    }

    public ApiKey(Organization org, String name, String keyHash, String keyPrefix, String keyLast4,
                  ApiKeyStatus status, Instant createdAt, Instant expiresAt, Long createdBy) {
        this.org = org;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.keyLast4 = keyLast4;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrg() {
        return org;
    }

    public String getName() {
        return name;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyLast4() {
        return keyLast4;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public String getLastUsedPath() {
        return lastUsedPath;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Long getRotatedFromId() {
        return rotatedFromId;
    }

    public void revoke(Instant now) {
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = now;
    }

    public void rotateFrom(Long rotatedFromId) {
        this.rotatedFromId = rotatedFromId;
    }

    public void expire(Instant now) {
        this.status = ApiKeyStatus.EXPIRED;
    }

    public void recordUse(String path, Instant now) {
        this.lastUsedAt = now;
        this.lastUsedPath = path;
    }

    public String maskedValue() {
        return keyPrefix + "…" + keyLast4;
    }
}
