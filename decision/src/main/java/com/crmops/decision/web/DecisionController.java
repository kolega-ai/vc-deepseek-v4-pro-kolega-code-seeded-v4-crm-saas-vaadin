package com.crmops.decision.web;

import com.crmops.common.api.Dto.AuthorizeRequest;
import com.crmops.common.api.Dto.AuthorizeResult;
import com.crmops.common.api.Dto.LoginRequest;
import com.crmops.common.api.Dto.LoginResponse;
import com.crmops.common.api.Dto.ScopeRequest;
import com.crmops.common.api.Dto.ScopeResult;
import com.crmops.common.api.Dto.ScopeTokenRequest;
import com.crmops.common.api.Dto.ScopeTokenResult;
import com.crmops.common.api.Dto.SyncRequest;
import com.crmops.common.api.Dto.SyncResponse;
import com.crmops.decision.domain.OrgAssignment;
import com.crmops.decision.repo.OrgAssignmentRepository;
import com.crmops.decision.security.CrmPrincipal;
import com.crmops.decision.service.AuthzDeniedException;
import com.crmops.decision.service.AuthorizeService;
import com.crmops.decision.service.LoginFailedException;
import com.crmops.decision.service.LoginService;
import com.crmops.decision.service.SyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DecisionController {

    private final LoginService loginService;
    private final AuthorizeService authorizeService;
    private final SyncService syncService;
    private final OrgAssignmentRepository assignmentRepo;

    public DecisionController(LoginService loginService, AuthorizeService authorizeService,
                              SyncService syncService, OrgAssignmentRepository assignmentRepo) {
        this.loginService = loginService;
        this.authorizeService = authorizeService;
        this.syncService = syncService;
        this.assignmentRepo = assignmentRepo;
    }

    /** Channel hands off credentials; decision verifies them and mints the session token. */
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return loginService.login(request.username(), request.password(), http.getRemoteAddr());
    }

    /** Per-request authorization check. Identity comes from the signed token + local DB only. */
    @PostMapping("/authorize")
    public AuthorizeResult authorize(@Valid @RequestBody AuthorizeRequest request, Authentication authentication) {
        CrmPrincipal principal = (CrmPrincipal) authentication.getPrincipal();
        return authorizeService.authorize(principal.userId(), request.action(), request.orgId());
    }

    /** Returns the set of organizations the caller may see for a list-style action. */
    @PostMapping("/authorize/scope")
    public ScopeResult scope(@Valid @RequestBody ScopeRequest request, Authentication authentication) {
        CrmPrincipal principal = (CrmPrincipal) authentication.getPrincipal();
        return authorizeService.scope(principal.userId(), request.action());
    }

    /** Mints a bounded scope token for core-mock calls; each org must be inside the caller's scope. */
    @PostMapping("/tokens/scope")
    public ScopeTokenResult scopeToken(@Valid @RequestBody ScopeTokenRequest request, Authentication authentication) {
        CrmPrincipal principal = (CrmPrincipal) authentication.getPrincipal();
        return authorizeService.mintScopeToken(principal.userId(), request);
    }

    /** Authorizes an API-key request against the synced key records. */
    @PostMapping("/authorize/apikey")
    public AuthorizeResult authorizeApiKey(@RequestBody Map<String, Object> body) {
        String keyHash = (String) body.get("keyHash");
        String action = (String) body.get("action");
        Long orgId = body.get("orgId") == null ? null : ((Number) body.get("orgId")).longValue();
        return authorizeService.authorizeApiKey(keyHash, action, orgId);
    }

    /** Full snapshot replace of identity, assignment and API-key data (service-authenticated). */
    @PostMapping("/sync")
    public SyncResponse sync(@Valid @RequestBody SyncRequest request) {
        return syncService.replace(request);
    }

    /** Batch support: owners of an organization, used for renewal notifications. */
    @GetMapping("/service/orgs/{orgId}/owners")
    public List<Long> orgOwners(@PathVariable long orgId) {
        return assignmentRepo.findByOrgId(orgId).stream()
                .filter(a -> OrgAssignment.RELATION_OWNER.equals(a.getRelation()))
                .map(OrgAssignment::getUserId)
                .toList();
    }

    @ExceptionHandler(LoginFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> loginFailed(LoginFailedException e) {
        return Map.of("code", "auth-failed", "message", e.getMessage());
    }

    @ExceptionHandler(AuthzDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> denied(AuthzDeniedException e) {
        return Map.of("code", "scope-denied", "message", e.getMessage());
    }
}
