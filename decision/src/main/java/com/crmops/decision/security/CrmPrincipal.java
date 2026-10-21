package com.crmops.decision.security;

/** Authenticated principal resolved from a signed session token plus database state. */
public record CrmPrincipal(long userId, String username, String role, Long customerOrgId) {
}
