package com.crmops.channel.workflow;

import com.crmops.channel.domain.DealStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealStageMachineTest {

    @Test
    void allowedEdgesWork() {
        assertEquals(DealStage.QUALIFIED, DealStageMachine.target(DealStage.LEAD, DealStageMachine.ACTION_QUALIFY));
        assertEquals(DealStage.PROPOSAL, DealStageMachine.target(DealStage.QUALIFIED, DealStageMachine.ACTION_PROPOSE));
        assertEquals(DealStage.NEGOTIATION, DealStageMachine.target(DealStage.PROPOSAL, DealStageMachine.ACTION_NEGOTIATE));
        assertEquals(DealStage.CLOSED_WON, DealStageMachine.target(DealStage.NEGOTIATION, DealStageMachine.ACTION_WIN));
        assertEquals(DealStage.RENEWAL_PENDING, DealStageMachine.target(DealStage.CLOSED_WON, DealStageMachine.ACTION_MARK_RENEWAL_PENDING));
        assertEquals(DealStage.CLOSED_WON, DealStageMachine.target(DealStage.RENEWAL_PENDING, DealStageMachine.ACTION_RENEW));
        assertEquals(DealStage.LEAD, DealStageMachine.target(DealStage.CLOSED_LOST, DealStageMachine.ACTION_RECYCLE));
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThrows(IllegalTransitionException.class,
                () -> DealStageMachine.target(DealStage.LEAD, DealStageMachine.ACTION_WIN));
        assertThrows(IllegalTransitionException.class,
                () -> DealStageMachine.target(DealStage.CLOSED_WON, DealStageMachine.ACTION_QUALIFY));
        assertFalse(DealStageMachine.isAllowed(DealStage.RENEWAL_PENDING, DealStageMachine.ACTION_PROPOSE));
    }

    @Test
    void everyStageHasActionsOrIsTerminal() {
        for (DealStage stage : DealStage.values()) {
            if (stage == DealStage.RENEWAL_PENDING || stage == DealStage.CLOSED_WON) {
                assertTrue(DealStageMachine.allowedActions(stage).size() >= 1,
                        "stage " + stage + " should have at least one action");
            }
        }
    }
}
