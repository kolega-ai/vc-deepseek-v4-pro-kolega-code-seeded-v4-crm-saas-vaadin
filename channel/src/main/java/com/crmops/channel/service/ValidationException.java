package com.crmops.channel.service;

/** A domain validation rule was violated. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
