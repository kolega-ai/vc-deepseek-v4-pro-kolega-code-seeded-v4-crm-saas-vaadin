package com.crmops.channel.domain;

public enum AssignmentRelation {
    OWNER("Owner"),
    SUPPORT("Support");

    private final String label;

    AssignmentRelation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
