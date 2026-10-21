package com.crmops.channel.domain;

public enum Severity {
    SEV1("SEV1 · Critical", 1),
    SEV2("SEV2 · High", 2),
    SEV3("SEV3 · Normal", 3);

    private final String label;
    private final int rank;

    Severity(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }
}
