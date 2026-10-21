package com.crmops.decision.service;

/** Thrown when a scope-token mint request is outside the user's granted scope. */
public class AuthzDeniedException extends RuntimeException {

    public AuthzDeniedException(String message) {
        super(message);
    }
}
