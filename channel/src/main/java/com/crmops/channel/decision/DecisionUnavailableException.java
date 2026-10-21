package com.crmops.channel.decision;

/** The decision tier is unreachable or unhealthy. */
public class DecisionUnavailableException extends RuntimeException {

    public DecisionUnavailableException(String message) {
        super(message);
    }

    public DecisionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
