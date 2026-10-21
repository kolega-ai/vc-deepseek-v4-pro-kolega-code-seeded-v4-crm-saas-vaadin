package com.crmops.channel.service;

/** The requested domain object does not exist or is outside the caller's scope. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " " + id + " not found");
    }
}
