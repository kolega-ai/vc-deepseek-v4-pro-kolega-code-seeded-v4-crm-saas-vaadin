package com.crmops.common.core;

/**
 * Canonical role names shared across tiers. The {@code decision} tier is the
 * authoritative source of a user's role; the {@code channel} tier only ever
 * receives the role as part of a signed, decision-minted session token.
 */
public final class Roles {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ACCOUNT_MANAGER = "ACCOUNT_MANAGER";
    public static final String SUPPORT_AGENT = "SUPPORT_AGENT";
    public static final String CUSTOMER_CONTACT = "CUSTOMER_CONTACT";

    /** Roles that belong to the operating company (as opposed to customers). */
    public static boolean isStaff(String role) {
        return PLATFORM_ADMIN.equals(role) || ACCOUNT_MANAGER.equals(role) || SUPPORT_AGENT.equals(role);
    }

    public static boolean isAdmin(String role) {
        return PLATFORM_ADMIN.equals(role);
    }

    private Roles() {
    }
}
