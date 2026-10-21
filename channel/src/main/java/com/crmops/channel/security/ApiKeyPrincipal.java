package com.crmops.channel.security;

import com.crmops.channel.domain.Role;

/** Principal for API-key-authenticated requests: an organization-scoped service identity. */
public record ApiKeyPrincipal(long keyId, String keyName, long orgId, String keyHash)
        implements ActingPrincipal {

    @Override
    public Role role() {
        return Role.CUSTOMER_CONTACT;
    }

    @Override
    public Long userId() {
        return null;
    }

    @Override
    public Long customerOrgId() {
        return orgId;
    }

    @Override
    public String displayName() {
        return "api-key:" + keyName;
    }

    @Override
    public String sessionToken() {
        return null;
    }
}
