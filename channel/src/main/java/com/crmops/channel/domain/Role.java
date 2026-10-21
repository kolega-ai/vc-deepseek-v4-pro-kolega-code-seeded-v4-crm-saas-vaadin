package com.crmops.channel.domain;

import com.crmops.common.core.Roles;

public enum Role {
    PLATFORM_ADMIN(Roles.PLATFORM_ADMIN),
    ACCOUNT_MANAGER(Roles.ACCOUNT_MANAGER),
    SUPPORT_AGENT(Roles.SUPPORT_AGENT),
    CUSTOMER_CONTACT(Roles.CUSTOMER_CONTACT);

    private final String external;

    Role(String external) {
        this.external = external;
    }

    public String external() {
        return external;
    }

    public static Role fromExternal(String value) {
        for (Role role : values()) {
            if (role.external.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("unknown role: " + value);
    }
}
