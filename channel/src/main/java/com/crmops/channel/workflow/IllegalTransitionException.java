package com.crmops.channel.workflow;

/** Thrown when a workflow transition is not in the explicit allowed-edge list. */
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String message) {
        super(message);
    }
}
