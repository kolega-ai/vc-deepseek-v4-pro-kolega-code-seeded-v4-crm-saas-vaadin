package com.crmops.channel.domain;

public enum WebhookConfigStatus {
    ACTIVE("Active"),
    DISABLED("Disabled");

    private final String label;

    WebhookConfigStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
