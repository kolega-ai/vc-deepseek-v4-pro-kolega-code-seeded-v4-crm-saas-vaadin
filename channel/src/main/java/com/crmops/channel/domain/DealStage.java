package com.crmops.channel.domain;

public enum DealStage {
    LEAD("Lead"),
    QUALIFIED("Qualified"),
    PROPOSAL("Proposal"),
    NEGOTIATION("Negotiation"),
    CLOSED_WON("Closed won"),
    CLOSED_LOST("Closed lost"),
    RENEWAL_PENDING("Renewal pending");

    private final String label;

    DealStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
