package com.crmops.channel.domain;

public enum DocCategory {
    CONTRACT("Contract"),
    SECURITY_QUESTIONNAIRE("Security questionnaire"),
    INVOICE("Invoice"),
    ONBOARDING("Onboarding"),
    OTHER("Other");

    private final String label;

    DocCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
