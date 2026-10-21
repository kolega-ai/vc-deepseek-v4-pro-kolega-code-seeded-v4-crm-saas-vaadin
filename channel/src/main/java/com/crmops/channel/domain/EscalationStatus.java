package com.crmops.channel.domain;

public enum EscalationStatus {
    OPEN("Open"),
    WAITING_ON_CUSTOMER("Waiting on customer"),
    WAITING_ON_INTERNAL("Waiting on internal"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    private final String label;

    EscalationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
