package com.crmops.channel.domain;

public enum DocVisibility {
    INTERNAL("Internal"),
    CUSTOMER_VISIBLE("Customer visible"),
    BILLING_ONLY("Billing only");

    private final String label;

    DocVisibility(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
