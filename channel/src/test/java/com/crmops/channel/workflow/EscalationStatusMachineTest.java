package com.crmops.channel.workflow;

import com.crmops.channel.domain.EscalationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscalationStatusMachineTest {

    @Test
    void allowedEdgesWork() {
        assertEquals(EscalationStatus.WAITING_ON_CUSTOMER,
                EscalationStatusMachine.target(EscalationStatus.OPEN, EscalationStatusMachine.ACTION_WAIT_ON_CUSTOMER));
        assertEquals(EscalationStatus.RESOLVED,
                EscalationStatusMachine.target(EscalationStatus.OPEN, EscalationStatusMachine.ACTION_RESOLVE));
        assertEquals(EscalationStatus.CLOSED,
                EscalationStatusMachine.target(EscalationStatus.RESOLVED, EscalationStatusMachine.ACTION_CLOSE));
        assertEquals(EscalationStatus.OPEN,
                EscalationStatusMachine.target(EscalationStatus.RESOLVED, EscalationStatusMachine.ACTION_REOPEN));
        assertEquals(EscalationStatus.OPEN,
                EscalationStatusMachine.target(EscalationStatus.WAITING_ON_CUSTOMER,
                        EscalationStatusMachine.ACTION_CUSTOMER_REPLIED));
    }

    @Test
    void closedIsTerminal() {
        assertTrue(EscalationStatusMachine.allowedActions(EscalationStatus.CLOSED).isEmpty());
    }

    @Test
    void illegalTransitionsAreRejected() {
        assertThrows(IllegalTransitionException.class,
                () -> EscalationStatusMachine.target(EscalationStatus.CLOSED, EscalationStatusMachine.ACTION_REOPEN));
        assertThrows(IllegalTransitionException.class,
                () -> EscalationStatusMachine.target(EscalationStatus.RESOLVED,
                        EscalationStatusMachine.ACTION_WAIT_ON_CUSTOMER));
    }

    @Test
    void everyStatusIsReachable() {
        // OPEN reaches WAITING_ON_* and RESOLVED; RESOLVED reaches CLOSED.
        assertEquals(EscalationStatus.WAITING_ON_INTERNAL,
                EscalationStatusMachine.target(EscalationStatus.OPEN, EscalationStatusMachine.ACTION_WAIT_ON_INTERNAL));
        assertEquals(EscalationStatus.RESOLVED,
                EscalationStatusMachine.target(EscalationStatus.WAITING_ON_INTERNAL, EscalationStatusMachine.ACTION_RESOLVE));
        assertEquals(EscalationStatus.CLOSED,
                EscalationStatusMachine.target(EscalationStatus.RESOLVED, EscalationStatusMachine.ACTION_CLOSE));
    }
}
