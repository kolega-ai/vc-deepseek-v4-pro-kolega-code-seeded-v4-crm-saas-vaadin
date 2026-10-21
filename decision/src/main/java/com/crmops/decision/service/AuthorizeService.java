package com.crmops.decision.service;

import com.crmops.common.api.Dto.AuthorizeResult;
import com.crmops.common.api.Dto.ScopeResult;
import com.crmops.common.api.Dto.ScopeTokenRequest;
import com.crmops.common.api.Dto.ScopeTokenResult;
import com.crmops.common.auth.SignedToken;
import com.crmops.common.core.Actions;
import com.crmops.common.core.Roles;
import com.crmops.decision.config.DecisionProperties;
import com.crmops.decision.domain.ApiKeyRecord;
import com.crmops.decision.domain.AuthUser;
import com.crmops.decision.domain.AuthzEvent;
import com.crmops.decision.domain.OrgAssignment;
import com.crmops.decision.policy.Policy;
import com.crmops.decision.repo.ApiKeyRecordRepository;
import com.crmops.decision.repo.AuthUserRepository;
import com.crmops.decision.repo.AuthzEventRepository;
import com.crmops.decision.repo.OrgAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates authorization requests. The acting user is ALWAYS loaded from the
 * local database inside this service on every request — the signed token only
 * names the user; it is never a substitute for the current database state
 * (a disabled account or a revoked assignment takes effect immediately).
 */
@Service
public class AuthorizeService {

    private final AuthUserRepository userRepo;
    private final OrgAssignmentRepository assignmentRepo;
    private final ApiKeyRecordRepository apiKeyRepo;
    private final AuthzEventRepository authzRepo;
    private final DecisionProperties properties;

    public AuthorizeService(AuthUserRepository userRepo, OrgAssignmentRepository assignmentRepo,
                            ApiKeyRecordRepository apiKeyRepo, AuthzEventRepository authzRepo,
                            DecisionProperties properties) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.apiKeyRepo = apiKeyRepo;
        this.authzRepo = authzRepo;
        this.properties = properties;
    }

    @Transactional
    public AuthorizeResult authorize(long userId, String action, Long orgId) {
        AuthUser user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return deny(null, "unknown user", action, orgId, "no such user");
        }
        if (!user.isEnabled()) {
            return deny(user, "disabled", action, orgId, "role=" + user.getRole() + "; account disabled");
        }
        Set<String> allowed = Policy.allowedActions(user.getRole());
        if (!allowed.contains(action)) {
            return deny(user, "policy", action, orgId, "role=" + user.getRole() + " lacks action " + action);
        }
        if (orgId != null && !Roles.isAdmin(user.getRole())
                && !Policy.bypassesOrgScope(user.getRole(), action)) {
            if (!hasOrgAccess(user, orgId)) {
                return deny(user, "scope", action, orgId, "role=" + user.getRole() + "; no access to org " + orgId);
            }
        }
        String basis = Roles.isAdmin(user.getRole())
                ? "role=PLATFORM_ADMIN; scope=*"
                : "role=" + user.getRole() + "; subject=" + user.getId() + (orgId != null ? "; org=" + orgId : "");
        return allow(user, action, orgId, basis);
    }

    @Transactional
    public ScopeResult scope(long userId, String action) {
        AuthUser user = userRepo.findById(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            return new ScopeResult(action, false, List.of());
        }
        if (!Policy.allowedActions(user.getRole()).contains(action)) {
            return new ScopeResult(action, false, List.of());
        }
        if (Roles.isAdmin(user.getRole()) || Policy.bypassesOrgScope(user.getRole(), action)) {
            return new ScopeResult(action, true, List.of());
        }
        if (Roles.CUSTOMER_CONTACT.equals(user.getRole())) {
            // Customer scope is their own organization, held on the identity
            // record itself — not in the assignments table.
            return new ScopeResult(action, false,
                    user.getCustomerOrgId() == null ? List.of() : List.of(user.getCustomerOrgId()));
        }
        List<Long> orgIds = assignmentRepo.findByUserId(userId).stream()
                .map(OrgAssignment::getOrgId)
                .distinct()
                .sorted()
                .toList();
        return new ScopeResult(action, false, orgIds);
    }

    @Transactional
    public ScopeTokenResult mintScopeToken(long userId, ScopeTokenRequest request) {
        AuthUser user = userRepo.findById(userId).orElse(null);
        if (user == null || !user.isEnabled()) {
            throw new AuthzDeniedException("account is not active");
        }
        // Every org in the request must be inside the user's granted scope for
        // the requested resource scope; org ids are never taken on trust.
        for (Long orgId : request.orgIds()) {
            AuthorizeResult verdict = authorize(userId, Actions.BILLING_READ, orgId);
            if (!verdict.allowed()) {
                throw new AuthzDeniedException("org " + orgId + " outside granted scope for " + request.scope());
            }
        }
        Map<String, Object> claims = Map.of(
                SignedToken.CLAIM_SUB, String.valueOf(userId),
                SignedToken.CLAIM_USERNAME, user.getUsername(),
                SignedToken.CLAIM_ROLE, user.getRole(),
                SignedToken.CLAIM_ORG_IDS, request.orgIds(),
                SignedToken.CLAIM_SCOPE, request.scope(),
                SignedToken.CLAIM_TYPE, SignedToken.TYPE_SCOPE);
        String token = SignedToken.mint(claims,
                Duration.ofMinutes(properties.scopeTokenTtlMinutes()),
                "decision",
                properties.coreTokenSecret());
        return new ScopeTokenResult(token, properties.scopeTokenTtlMinutes() * 60);
    }

    /**
     * Authorizes an API-key request. Channel resolves the raw key against its own
     * table (the key secret never leaves channel), then presents the key hash.
     * This service re-verifies the key record from its own database and enforces
     * the key's single-organization scope.
     */
    @Transactional
    public AuthorizeResult authorizeApiKey(String keyHash, String action, Long orgId) {
        ApiKeyRecord key = apiKeyRepo.findByKeyHash(keyHash).orElse(null);
        if (key == null || !"active".equalsIgnoreCase(key.getStatus())) {
            return new AuthorizeResult(false, action, orgId, "api-key unknown or not active", "unknown or inactive api key");
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) {
            return new AuthorizeResult(false, action, orgId, "api-key expired", "api key expired");
        }
        if (!Policy.API_KEY_ACTIONS.contains(action)) {
            return new AuthorizeResult(false, action, orgId, "api-key scope lacks action", "api keys cannot perform " + action);
        }
        if (orgId == null || !orgId.equals(key.getOrgId())) {
            return new AuthorizeResult(false, action, orgId, "api-key org mismatch",
                    "api key scoped to org " + key.getOrgId());
        }
        authz("api-key:" + key.getName(), "CUSTOMER_CONTACT", action, orgId, true,
                "api-key; scope=org:" + key.getOrgId());
        return new AuthorizeResult(true, action, orgId,
                "api-key; scope=org:" + key.getOrgId(), null);
    }

    private boolean hasOrgAccess(AuthUser user, Long orgId) {
        if (Roles.CUSTOMER_CONTACT.equals(user.getRole())) {
            return orgId.equals(user.getCustomerOrgId());
        }
        return assignmentRepo.findByUserId(user.getId()).stream()
                .anyMatch(a -> a.getOrgId().equals(orgId));
    }

    private AuthorizeResult allow(AuthUser user, String action, Long orgId, String basis) {
        authz(user, action, orgId, true, basis);
        return new AuthorizeResult(true, action, orgId, basis, null);
    }

    private AuthorizeResult deny(AuthUser user, String principal, String action, Long orgId, String basis) {
        authz(user == null ? null : user, action, orgId, false, basis);
        return new AuthorizeResult(false, action, orgId, basis, "denied: " + basis);
    }

    private void authz(AuthUser user, String action, Long orgId, boolean allowed, String basis) {
        authz(user == null ? null : user.getUsername(), user == null ? null : user.getRole(), action, orgId, allowed, basis);
    }

    private void authz(String principal, String role, String action, Long orgId, boolean allowed, String basis) {
        authzRepo.save(new AuthzEvent(null, null, role, principal, action, orgId, allowed, basis));
    }
}
