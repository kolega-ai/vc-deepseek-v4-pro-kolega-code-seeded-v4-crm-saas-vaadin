package com.crmops.decision.service;

import com.crmops.common.api.Dto.LoginResponse;
import com.crmops.common.api.Dto.UserInfo;
import com.crmops.common.auth.LoginAttemptGuard;
import com.crmops.common.auth.SignedToken;
import com.crmops.decision.config.DecisionProperties;
import com.crmops.decision.domain.AuthUser;
import com.crmops.decision.repo.AuthUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Verifies credentials against the local identity database and mints the
 * opaque session token the channel tier presents on every authorization call.
 */
@Service
public class LoginService {

    private final AuthUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final DecisionProperties properties;
    private final LoginAttemptGuard guard;

    public LoginService(AuthUserRepository userRepo, PasswordEncoder passwordEncoder,
                        DecisionProperties properties, LoginAttemptGuard guard) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.guard = guard;
    }

    public LoginResponse login(String username, String password, String clientAddress) {
        if (guard.isLocked(username, clientAddress)) {
            throw new LoginFailedException("too many attempts; try again later");
        }
        AuthUser user = userRepo.findByUsernameIgnoreCase(username == null ? "" : username.trim()).orElse(null);
        // Same generic failure for unknown user and wrong password so account
        // enumeration through the login endpoint is not possible.
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            guard.recordFailure(username, clientAddress);
            throw new LoginFailedException("invalid credentials");
        }
        guard.recordSuccess(username, clientAddress);
        Map<String, Object> claims = Map.of(
                SignedToken.CLAIM_SUB, String.valueOf(user.getId()),
                SignedToken.CLAIM_USERNAME, user.getUsername(),
                SignedToken.CLAIM_ROLE, user.getRole(),
                SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION);
        if (user.getCustomerOrgId() != null) {
            claims = new java.util.HashMap<>(claims);
            claims.put(SignedToken.CLAIM_CUSTOMER_ORG, user.getCustomerOrgId());
        }
        long ttlMinutes = properties.sessionTokenTtlMinutes();
        String token = SignedToken.mint(claims, Duration.ofMinutes(ttlMinutes), "decision", properties.sessionTokenSecret());
        // Session tokens are signed with the decision tier's private session
        // secret and carry typ=session; core-mock only ever accepts typ=scope
        // tokens signed with the separate core-token secret.
        return new LoginResponse(token, ttlMinutes * 60, toInfo(user));
    }

    public static UserInfo toInfo(AuthUser user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getRole(), user.isEnabled(), user.getCustomerOrgId());
    }
}
