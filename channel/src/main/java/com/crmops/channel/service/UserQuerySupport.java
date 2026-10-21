package com.crmops.channel.service;

import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.UserAccount;
import org.springframework.data.jpa.domain.Specification;

/** Read-side helpers for user and assignment data. */
public final class UserQuerySupport {

    private UserQuerySupport() {
    }

    public static Specification<UserAccount> filter(String search, Role role, Boolean enabled) {
        Specification<UserAccount> spec = Specification.where(null);
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("displayName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (enabled != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("enabled"), enabled));
        }
        return spec;
    }
}
