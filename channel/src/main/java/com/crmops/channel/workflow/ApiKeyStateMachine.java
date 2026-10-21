package com.crmops.channel.workflow;

import com.crmops.channel.domain.ApiKeyStatus;

import java.util.Map;
import java.util.Set;

/**
 * API key lifecycle. Active keys may be revoked by an operator or expired by
 * the batch expiry job; both terminal states are irreversible.
 */
public final class ApiKeyStateMachine {

    public static final String ACTION_REVOKE = "revoke";
    public static final String ACTION_EXPIRE = "expire";

    private static final Map<ApiKeyStatus, Set<String>> EDGES = Map.of(
            ApiKeyStatus.ACTIVE, Set.of(ACTION_REVOKE, ACTION_EXPIRE),
            ApiKeyStatus.REVOKED, Set.of(),
            ApiKeyStatus.EXPIRED, Set.of());

    public static Set<String> allowedActions(ApiKeyStatus status) {
        return EDGES.getOrDefault(status, Set.of());
    }

    public static boolean isAllowed(ApiKeyStatus from, String action) {
        return EDGES.getOrDefault(from, Set.of()).contains(action);
    }

    public static void requireAllowed(ApiKeyStatus from, String action) {
        if (!isAllowed(from, action)) {
            throw new IllegalTransitionException(
                    "transition '" + action + "' is not allowed from status " + from);
        }
    }
}
