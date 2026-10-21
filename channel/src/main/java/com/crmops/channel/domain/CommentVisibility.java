package com.crmops.channel.domain;

public enum CommentVisibility {
    INTERNAL("Internal"),
    CUSTOMER_VISIBLE("Customer visible");

    private final String label;

    CommentVisibility(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
