package com.crmops.channel.domain;

public enum OutboxStatus {
    PENDING("Pending"),
    SENT("Sent"),
    FAILED("Failed");

    private final String label;

    OutboxStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
