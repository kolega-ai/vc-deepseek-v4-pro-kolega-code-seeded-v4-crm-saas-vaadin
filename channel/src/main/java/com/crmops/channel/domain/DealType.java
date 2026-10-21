package com.crmops.channel.domain;

public enum DealType {
    NEW_BUSINESS("New business"),
    RENEWAL("Renewal");

    private final String label;

    DealType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
