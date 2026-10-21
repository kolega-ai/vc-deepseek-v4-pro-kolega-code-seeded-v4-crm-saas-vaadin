package com.crmops.common.api;

import java.util.List;

/** API DTOs exchanged between the channel, decision and core-mock tiers. */
public final class Dto {

    private Dto() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record UserInfo(long id, String username, String displayName, String email, String role,
                           boolean enabled, Long customerOrgId) {
    }

    public record LoginResponse(String token, long expiresInSeconds, UserInfo user) {
    }

    public record AuthorizeRequest(String action, Long orgId) {
    }

    public record AuthorizeResult(boolean allowed, String action, Long orgId, String authorizationBasis,
                                  String reason) {
    }

    public record ScopeRequest(String action) {
    }

    public record ScopeResult(String action, boolean unrestricted, List<Long> allowedOrgIds) {
    }

    public record ScopeTokenRequest(List<Long> orgIds, String scope) {
    }

    public record ScopeTokenResult(String token, long expiresInSeconds) {
    }

    public record SyncUser(long id, String username, String displayName, String email, String role,
                           boolean enabled, Long customerOrgId, String passwordHash) {
    }

    public record SyncAssignment(long userId, long orgId, String relation) {
    }

    public record SyncApiKey(String keyHash, long orgId, String name, String status, java.time.Instant expiresAt) {
    }

    public record SyncRequest(List<SyncUser> users, List<SyncAssignment> assignments, List<SyncApiKey> apiKeys) {
    }

    public record SyncResponse(int users, int assignments, int apiKeys) {
    }

    public record Invoice(long id, long orgId, String number, java.math.BigDecimal amount, String currency,
                          String status, java.time.Instant issuedAt, java.time.Instant dueAt,
                          String periodStart, String periodEnd) {
    }

    public record InvoiceList(long orgId, String currency, List<Invoice> invoices) {
    }

    public record ErrorBody(String code, String message) {
    }

    public record WebhookDeliveryRecord(long id, String tenant, String eventType, String payloadDigest,
                                        java.time.Instant receivedAt) {
    }
}
