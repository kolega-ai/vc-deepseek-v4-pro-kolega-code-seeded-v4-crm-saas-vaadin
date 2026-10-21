package com.crmops.channel.security;

import com.crmops.channel.domain.Role;

/**
 * Union of the two channel-tier principal kinds:
 * <ul>
 *   <li>{@link CrmPrincipal} — a logged-in user with a decision-minted session token</li>
 *   <li>{@link ApiKeyPrincipal} — an organization-scoped service identity authenticated by API key</li>
 * </ul>
 * Both flow through the same authorization gateway; the gateway branches on
 * which credential to present to the decision tier.
 */
public sealed interface ActingPrincipal permits CrmPrincipal, ApiKeyPrincipal {

    Role role();

    /** Human user id, or {@code null} for API keys. */
    Long userId();

    /** The organization a customer actor is bound to (API keys are always bound). */
    Long customerOrgId();

    String displayName();

    /** Opaque decision session token, or {@code null} for API keys. */
    String sessionToken();

    /** SHA-256 hash of the API key, or {@code null} for users. */
    String keyHash();

    default boolean isSystem() {
        return userId() != null && userId() == 0L;
    }
}
