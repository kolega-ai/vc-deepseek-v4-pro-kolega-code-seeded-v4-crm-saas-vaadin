package com.crmops.channel.domain;

public enum OrgStatus {
    ACTIVE("Active"),
    TRIAL("Trial"),
    SUSPENDED("Suspended"),
    CHURNED("Churned");

    private final String label;

    OrgStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
