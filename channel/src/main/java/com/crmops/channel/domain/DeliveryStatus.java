package com.crmops.channel.domain;

public enum DeliveryStatus {
    PENDING("Pending"),
    SUCCESS("Delivered"),
    FAILED("Failed");

    private final String label;

    DeliveryStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
