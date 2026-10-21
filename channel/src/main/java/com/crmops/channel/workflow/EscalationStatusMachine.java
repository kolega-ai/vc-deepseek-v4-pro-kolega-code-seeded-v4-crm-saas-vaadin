package com.crmops.channel.workflow;

import com.crmops.channel.domain.EscalationStatus;

import java.util.Map;
import java.util.Set;

/**
 * Escalation lifecycle state machine with an explicit allowed-edge list.
 * Every status used anywhere in the application is reachable through these
 * edges; the seeder produces seeded statuses by invoking the same actions.
 */
public final class EscalationStatusMachine {

    public static final String ACTION_WAIT_ON_CUSTOMER = "wait_on_customer";
    public static final String ACTION_WAIT_ON_INTERNAL = "wait_on_internal";
    public static final String ACTION_CUSTOMER_REPLIED = "customer_replied";
    public static final String ACTION_REOPEN = "reopen";
    public static final String ACTION_RESOLVE = "resolve";
    public static final String ACTION_CLOSE = "close";

    private static final Map<EscalationStatus, Map<String, EscalationStatus>> EDGES = Map.of(
            EscalationStatus.OPEN, Map.of(
                    ACTION_WAIT_ON_CUSTOMER, EscalationStatus.WAITING_ON_CUSTOMER,
                    ACTION_WAIT_ON_INTERNAL, EscalationStatus.WAITING_ON_INTERNAL,
                    ACTION_RESOLVE, EscalationStatus.RESOLVED),
            EscalationStatus.WAITING_ON_CUSTOMER, Map.of(
                    ACTION_WAIT_ON_INTERNAL, EscalationStatus.WAITING_ON_INTERNAL,
                    ACTION_CUSTOMER_REPLIED, EscalationStatus.OPEN,
                    ACTION_RESOLVE, EscalationStatus.RESOLVED),
            EscalationStatus.WAITING_ON_INTERNAL, Map.of(
                    ACTION_WAIT_ON_CUSTOMER, EscalationStatus.WAITING_ON_CUSTOMER,
                    ACTION_CUSTOMER_REPLIED, EscalationStatus.OPEN,
                    ACTION_RESOLVE, EscalationStatus.RESOLVED),
            EscalationStatus.RESOLVED, Map.of(
                    ACTION_CLOSE, EscalationStatus.CLOSED,
                    ACTION_REOPEN, EscalationStatus.OPEN));

    public static Set<String> allowedActions(EscalationStatus status) {
        return EDGES.getOrDefault(status, Map.of()).keySet();
    }

    public static boolean isAllowed(EscalationStatus from, String action) {
        return EDGES.getOrDefault(from, Map.of()).containsKey(action);
    }

    public static EscalationStatus target(EscalationStatus from, String action) {
        Map<String, EscalationStatus> actions = EDGES.getOrDefault(from, Map.of());
        if (!actions.containsKey(action)) {
            throw new IllegalTransitionException(
                    "transition '" + action + "' is not allowed from status " + from);
        }
        return actions.get(action);
    }
}
