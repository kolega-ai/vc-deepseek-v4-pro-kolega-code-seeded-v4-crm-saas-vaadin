package com.crmops.channel.workflow;

import com.crmops.channel.domain.DealStage;

import java.util.Map;
import java.util.Set;

/**
 * Deal pipeline state machine. The ALLOWED transitions are enumerated as an
 * explicit edge list — anything not listed here is rejected server-side, no
 * matter what a caller posts.
 */
public final class DealStageMachine {

    public static final String ACTION_QUALIFY = "qualify";
    public static final String ACTION_PROPOSE = "propose";
    public static final String ACTION_NEGOTIATE = "negotiate";
    public static final String ACTION_WIN = "win";
    public static final String ACTION_LOSE = "lose";
    public static final String ACTION_MARK_RENEWAL_PENDING = "mark_renewal_pending";
    public static final String ACTION_RENEW = "renew";
    public static final String ACTION_RENEGOTIATE = "renegotiate";
    public static final String ACTION_RECYCLE = "recycle";

    private static final Map<DealStage, Map<String, DealStage>> EDGES = Map.of(
            DealStage.LEAD, Map.of(ACTION_QUALIFY, DealStage.QUALIFIED, ACTION_LOSE, DealStage.CLOSED_LOST),
            DealStage.QUALIFIED, Map.of(ACTION_PROPOSE, DealStage.PROPOSAL, ACTION_LOSE, DealStage.CLOSED_LOST),
            DealStage.PROPOSAL, Map.of(ACTION_NEGOTIATE, DealStage.NEGOTIATION, ACTION_LOSE, DealStage.CLOSED_LOST),
            DealStage.NEGOTIATION, Map.of(ACTION_WIN, DealStage.CLOSED_WON, ACTION_LOSE, DealStage.CLOSED_LOST),
            DealStage.CLOSED_WON, Map.of(ACTION_MARK_RENEWAL_PENDING, DealStage.RENEWAL_PENDING),
            DealStage.RENEWAL_PENDING, Map.of(
                    ACTION_RENEW, DealStage.CLOSED_WON,
                    ACTION_LOSE, DealStage.CLOSED_LOST,
                    ACTION_RENEGOTIATE, DealStage.NEGOTIATION),
            DealStage.CLOSED_LOST, Map.of(ACTION_RECYCLE, DealStage.LEAD));

    public static Set<String> allowedActions(DealStage stage) {
        return EDGES.getOrDefault(stage, Map.of()).keySet();
    }

    public static boolean isAllowed(DealStage from, String action) {
        return EDGES.getOrDefault(from, Map.of()).containsKey(action);
    }

    public static DealStage target(DealStage from, String action) {
        Map<String, DealStage> actions = EDGES.getOrDefault(from, Map.of());
        if (!actions.containsKey(action)) {
            throw new IllegalTransitionException(
                    "transition '" + action + "' is not allowed from stage " + from);
        }
        return actions.get(action);
    }
}
