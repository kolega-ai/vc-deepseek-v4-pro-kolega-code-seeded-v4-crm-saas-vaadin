package com.crmops.channel.security;

import com.crmops.channel.domain.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Access to the acting principal. Also exposes the internal SYSTEM actor used
 * by seeders and background workflows; system actions are recorded in the
 * audit trail with a distinct basis so they can never masquerade as a user.
 */
public final class SecurityUtils {

    public static final CrmPrincipal SYSTEM = new CrmPrincipal(
            0L, "system", "System", "system@crmops.local", Role.PLATFORM_ADMIN, null, null);

    private SecurityUtils() {
    }

    public static ActingPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ActingPrincipal principal)) {
            return null;
        }
        return principal;
    }

    public static ActingPrincipal requireUser() {
        ActingPrincipal principal = current();
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("authentication required");
        }
        // NOTE: the SYSTEM actor is accepted here — it can only ever be placed
        // in the context by in-process code (seeders, jobs), never by an HTTP
        // request, and CrmAuthorization short-circuits it with an explicit
        // SYSTEM basis so it cannot masquerade as a user.
        return principal;
    }

    public static boolean isAdmin() {
        ActingPrincipal principal = current();
        return principal != null && principal.role() == Role.PLATFORM_ADMIN;
    }

    public static boolean hasRole(Role role) {
        ActingPrincipal principal = current();
        return principal != null && principal.role() == role;
    }
}
