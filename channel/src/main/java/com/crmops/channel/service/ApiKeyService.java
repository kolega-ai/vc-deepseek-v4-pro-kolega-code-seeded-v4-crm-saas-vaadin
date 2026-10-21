package com.crmops.channel.service;

import com.crmops.channel.domain.ApiKey;
import com.crmops.channel.domain.ApiKeyStatus;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.repo.ApiKeyRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.workflow.ApiKeyStateMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.API_KEY_CREATE;
import static com.crmops.common.core.Actions.API_KEY_READ;
import static com.crmops.common.core.Actions.API_KEY_REVOKE;
import static com.crmops.common.core.Actions.API_KEY_ROTATE;

/**
 * Organization-scoped API keys. The full secret is generated with
 * SecureRandom, shown exactly once at creation and stored only as a SHA-256
 * hash; after creation only the masked value (prefix + last four) is ever
 * displayed. Rotation creates a new key and revokes the old one through the
 * explicit ApiKeyStateMachine edge list, and both actions are audited and
 * synced to the decision tier.
 */
@Service
public class ApiKeyService {

    private final ApiKeyRepository keyRepo;
    private final OrganizationRepository orgRepo;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final DecisionSyncService sync;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository keyRepo, OrganizationRepository orgRepo, CrmAuthorization authz,
                         AuditService audit, DecisionSyncService sync) {
        this.keyRepo = keyRepo;
        this.orgRepo = orgRepo;
        this.authz = authz;
        this.audit = audit;
        this.sync = sync;
    }

    public record CreatedKey(ApiKey key, String plaintextSecret) {
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(long orgId) {
        authz.require(API_KEY_READ, orgId);
        return keyRepo.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    @Transactional
    public CreatedKey create(long orgId, String name, Instant expiresAt) {
        var verdict = authz.require(API_KEY_CREATE, orgId);
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new ValidationException("key name is required (max 120 characters)");
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new ValidationException("expiry must be in the future");
        }
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String secret = "crm_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = sha256(secret);
        Instant now = Instant.now();
        ApiKey key = new ApiKey(org, name.trim(), hash, secret.substring(0, 12), secret.substring(secret.length() - 4),
                ApiKeyStatus.ACTIVE, now, expiresAt, SecurityUtils.requireUser().userId());
        keyRepo.save(key);
        sync.fullSync();
        audit.record("api_key.create", "api_key", key.getId(), orgId, null,
                Map.of("name", key.getName(), "masked", key.maskedValue(),
                        "expiresAt", expiresAt == null ? "never" : expiresAt.toString()),
                verdict.authorizationBasis(), false);
        return new CreatedKey(key, secret);
    }

    @Transactional
    public CreatedKey rotate(long keyId) {
        ApiKey existing = keyRepo.findById(keyId).orElseThrow(() -> new NotFoundException("api key", keyId));
        var verdict = authz.require(API_KEY_ROTATE, existing.getOrg().getId());
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String secret = "crm_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = Instant.now();
        ApiKey replacement = new ApiKey(existing.getOrg(), existing.getName() + " (rotated)", sha256(secret),
                secret.substring(0, 12), secret.substring(secret.length() - 4), ApiKeyStatus.ACTIVE, now,
                existing.getExpiresAt(), SecurityUtils.requireUser().userId());
        replacement.rotateFrom(existing.getId());
        keyRepo.save(replacement);
        revokeInternal(existing, "rotated", now, verdict.authorizationBasis());
        sync.fullSync();
        audit.record("api_key.rotate", "api_key", keyId, existing.getOrg().getId(),
                Map.of("status", existing.getStatus().name()),
                Map.of("replacementId", replacement.getId(), "masked", replacement.maskedValue()),
                verdict.authorizationBasis(), false);
        return new CreatedKey(replacement, secret);
    }

    @Transactional
    public ApiKey revoke(long keyId) {
        ApiKey key = keyRepo.findById(keyId).orElseThrow(() -> new NotFoundException("api key", keyId));
        var verdict = authz.require(API_KEY_REVOKE, key.getOrg().getId());
        revokeInternal(key, "operator", Instant.now(), verdict.authorizationBasis());
        sync.fullSync();
        return key;
    }

    private void revokeInternal(ApiKey key, String reason, Instant now, String basis) {
        ApiKeyStateMachine.requireAllowed(key.getStatus(), ApiKeyStateMachine.ACTION_REVOKE);
        ApiKeyStatus before = key.getStatus();
        key.revoke(now);
        audit.record("api_key.revoke", "api_key", key.getId(), key.getOrg().getId(),
                Map.of("status", before.name()), Map.of("status", ApiKeyStatus.REVOKED.name(), "reason", reason),
                basis, false);
    }

    /** Batch expiry through the explicit machine edge list. */
    @Transactional
    public int expireDueKeys() {
        Instant now = Instant.now();
        int expired = 0;
        for (ApiKey key : keyRepo.findByStatusAndExpiresAtBefore(ApiKeyStatus.ACTIVE, now)) {
            ApiKeyStateMachine.requireAllowed(key.getStatus(), ApiKeyStateMachine.ACTION_EXPIRE);
            key.expire(now);
            expired++;
            audit.record("api_key.expire", "api_key", key.getId(), key.getOrg().getId(),
                    Map.of("status", ApiKeyStatus.ACTIVE.name()), Map.of("status", ApiKeyStatus.EXPIRED.name()),
                    "SYSTEM:batch:api-key-expiry", false);
        }
        if (expired > 0) {
            sync.fullSync();
        }
        return expired;
    }

    /** Resolves a presented raw key to an active record, or null. */
    @Transactional(readOnly = true)
    public ApiKey resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        return keyRepo.findByKeyHash(sha256(rawKey))
                .filter(k -> k.getStatus() == ApiKeyStatus.ACTIVE)
                .filter(k -> k.getExpiresAt() == null || k.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }

    @Transactional
    public void recordUse(long keyId, String path) {
        keyRepo.findById(keyId).ifPresent(key -> {
            if (key.getLastUsedAt() == null || key.getLastUsedAt().isBefore(Instant.now().minusSeconds(60))) {
                key.recordUse(path, Instant.now());
            }
        });
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
