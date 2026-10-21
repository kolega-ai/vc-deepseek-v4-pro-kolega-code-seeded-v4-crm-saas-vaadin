package com.crmops.channel.domain;

public enum ApiKeyStatus {
    ACTIVE("Active"),
    REVOKED("Revoked"),
    EXPIRED("Expired");

    private final String label;

    ApiKeyStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
