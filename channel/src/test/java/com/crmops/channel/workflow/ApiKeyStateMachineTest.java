package com.crmops.channel.workflow;

import com.crmops.channel.domain.ApiKeyStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyStateMachineTest {

    @Test
    void activeCanBeRevokedOrExpired() {
        assertTrue(ApiKeyStateMachine.isAllowed(ApiKeyStatus.ACTIVE, ApiKeyStateMachine.ACTION_REVOKE));
        assertTrue(ApiKeyStateMachine.isAllowed(ApiKeyStatus.ACTIVE, ApiKeyStateMachine.ACTION_EXPIRE));
        ApiKeyStateMachine.requireAllowed(ApiKeyStatus.ACTIVE, ApiKeyStateMachine.ACTION_REVOKE);
    }

    @Test
    void revokedAndExpiredAreTerminal() {
        assertThrows(IllegalTransitionException.class,
                () -> ApiKeyStateMachine.requireAllowed(ApiKeyStatus.REVOKED, ApiKeyStateMachine.ACTION_EXPIRE));
        assertThrows(IllegalTransitionException.class,
                () -> ApiKeyStateMachine.requireAllowed(ApiKeyStatus.EXPIRED, ApiKeyStateMachine.ACTION_REVOKE));
        assertTrue(ApiKeyStateMachine.allowedActions(ApiKeyStatus.REVOKED).isEmpty());
    }
}
