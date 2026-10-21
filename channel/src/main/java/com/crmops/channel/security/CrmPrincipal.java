package com.crmops.channel.security;

import com.crmops.common.api.Dto.UserInfo;
import com.crmops.channel.domain.Role;

/**
 * The authenticated principal for channel-tier requests. The session token is
 * the opaque decision-minted credential presented back to the decision tier on
 * every authorization call; it is never interpreted by the channel itself.
 */
public record CrmPrincipal(Long userId, String username, String displayName, String email,
                           Role role, Long customerOrgId, String sessionToken) implements ActingPrincipal {

    @Override
    public String keyHash() {
        return null;
    }

    public static CrmPrincipal fromLogin(com.crmops.common.api.Dto.LoginResponse response) {
        UserInfo user = response.user();
        return new CrmPrincipal(user.id(), user.username(), user.displayName(), user.email(),
                Role.fromExternal(user.role()), user.customerOrgId(), response.token());
    }
}
