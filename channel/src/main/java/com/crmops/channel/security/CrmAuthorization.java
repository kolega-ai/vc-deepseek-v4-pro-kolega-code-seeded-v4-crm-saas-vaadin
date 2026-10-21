package com.crmops.channel.security;

import com.crmops.common.api.Dto.AuthorizeResult;
import com.crmops.common.api.Dto.ScopeResult;
import com.crmops.channel.config.ChannelProperties;
import com.crmops.channel.decision.DecisionClient;
import com.crmops.channel.decision.DecisionUnavailableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The channel tier's single gateway to authorization. Every protected service
 * operation calls {@link #require} or {@link #orgScope} before touching data.
 * Verdicts come from the decision tier on every cache miss; results are cached
 * only briefly (see {@code app.authz-cache-seconds}) so role and assignment
 * changes take effect quickly while local grid queries stay responsive.
 * <p>
 * User sessions present their decision-minted session token; API-key requests
 * present the key hash and are re-verified by decision against its synced key
 * records (defense in depth on top of the channel-side key check).
 */
@Component
public class CrmAuthorization {

    private final DecisionClient client;
    private final long cacheTtlSeconds;
    private final Map<String, CachedVerdict> verdictCache = new ConcurrentHashMap<>();
    private final Map<String, CachedScope> scopeCache = new ConcurrentHashMap<>();

    public CrmAuthorization(DecisionClient client, ChannelProperties properties) {
        this.client = client;
        this.cacheTtlSeconds = properties.authzCacheSeconds();
    }

    /** Asserts the caller may perform the action (organization-independent). */
    public AuthorizeResult require(String action) {
        return require(action, null);
    }

    /** Asserts the caller may perform the action on the given organization. */
    public AuthorizeResult require(String action, Long orgId) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        if (principal.isSystem()) {
            return new AuthorizeResult(true, action, orgId, "SYSTEM:internal", null);
        }
        String key = cacheKey(principal, action, orgId);
        CachedVerdict cached = verdictCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            if (!cached.result().allowed()) {
                throw new AccessDeniedException(cached.result().reason());
            }
            return cached.result();
        }
        AuthorizeResult result;
        if (principal instanceof ApiKeyPrincipal apiKey) {
            result = client.authorizeApiKey(apiKey.keyHash(), action, orgId);
        } else {
            result = client.authorize(principal.sessionToken(), action, orgId);
        }
        verdictCache.put(key, new CachedVerdict(result, Instant.now().plusSeconds(cacheTtlSeconds)));
        if (!result.allowed()) {
            throw new AccessDeniedException(result.reason());
        }
        return result;
    }

    /**
     * Returns the set of organizations the caller may see for a list-style
     * action, or {@code null} when the caller's scope is unrestricted. Grid
     * data providers must intersect their queries with this set.
     */
    public List<Long> orgScope(String action) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        if (principal.isSystem()) {
            return null;
        }
        if (principal instanceof ApiKeyPrincipal apiKey) {
            return List.of(apiKey.orgId());
        }
        String key = principal.userId() + "|scope|" + action;
        CachedScope cached = scopeCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.orgIds();
        }
        ScopeResult result = client.scope(principal.sessionToken(), action);
        List<Long> orgIds = result.unrestricted() ? null : result.allowedOrgIds();
        scopeCache.put(key, new CachedScope(orgIds, Instant.now().plusSeconds(cacheTtlSeconds)));
        return orgIds;
    }

    /** Mints a decision-signed scope token for core-mock data access. */
    public String mintScopeToken(List<Long> orgIds, String scope) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        if (principal.isSystem() || principal instanceof ApiKeyPrincipal) {
            throw new AccessDeniedException("this principal cannot mint scope tokens");
        }
        return client.mintScopeToken(principal.sessionToken(), orgIds, scope).token();
    }

    /** Asserts the caller may perform the action; fails with a user-facing message when decision is down. */
    public void requireOrExplain(String action, Long orgId, String messageIfUnavailable) {
        try {
            require(action, orgId);
        } catch (DecisionUnavailableException e) {
            throw new DecisionUnavailableException(messageIfUnavailable, e);
        }
    }

    private static String cacheKey(ActingPrincipal principal, String action, Long orgId) {
        String id = principal instanceof ApiKeyPrincipal apiKey
                ? "key:" + apiKey.keyHash() : "user:" + principal.userId();
        return id + "|" + action + "|" + (orgId == null ? "-" : orgId);
    }

    private record CachedVerdict(AuthorizeResult result, Instant expiresAt) {
    }

    private record CachedScope(List<Long> orgIds, Instant expiresAt) {
    }
}
