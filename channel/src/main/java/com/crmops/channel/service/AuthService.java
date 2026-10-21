package com.crmops.channel.service;

import com.crmops.common.auth.LoginAttemptGuard;
import com.crmops.common.api.Dto.LoginResponse;
import com.crmops.channel.decision.DecisionClient;
import com.crmops.channel.security.CrmPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Login orchestration for the channel tier: the password is verified by the
 * decision tier (the credential verifier), and the returned session token is
 * stored in the HTTP session. The session id is rotated after login to prevent
 * session fixation.
 */
@Service
public class AuthService {

    private final DecisionClient decisionClient;
    private final SecurityContextRepository contextRepository;
    private final UserService userService;
    private final AuditService audit;
    private final LoginAttemptGuard attemptGuard = new LoginAttemptGuard();

    public AuthService(DecisionClient decisionClient, SecurityContextRepository contextRepository,
                       UserService userService, AuditService audit) {
        this.decisionClient = decisionClient;
        this.contextRepository = contextRepository;
        this.userService = userService;
        this.audit = audit;
    }

    public void login(String username, String password, HttpServletRequest request, HttpServletResponse response) {
        if (attemptGuard.isLocked(username, request.getRemoteAddr())) {
            throw new ValidationException("too many failed attempts; try again in 15 minutes");
        }
        LoginResponse loginResponse;
        try {
            loginResponse = decisionClient.login(username, password);
        } catch (DecisionClient.InvalidCredentialsException e) {
            attemptGuard.recordFailure(username, request.getRemoteAddr());
            throw new ValidationException("invalid username or password");
        }
        attemptGuard.recordSuccess(username, request.getRemoteAddr());
        CrmPrincipal principal = CrmPrincipal.fromLogin(loginResponse);
        userService.ensureMirror(loginResponse.user());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        // Rotate the session id: the pre-login session must not survive login.
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        audit.record("auth.login", "session", null, principal.customerOrgId(), null,
                Map.of("username", principal.username()), "decision-verified credentials", false);
    }

    /**
     * Signs a contact straight into the portal once they have accepted an
     * invitation and chosen their password, so they land on the dashboard
     * instead of being sent back to the sign-in form.
     */
    public void beginPortalSession(String username, String password, HttpServletRequest request,
                                   HttpServletResponse response) {
        LoginResponse loginResponse = decisionClient.login(username, password);
        CrmPrincipal principal = CrmPrincipal.fromLogin(loginResponse);
        userService.ensureMirror(loginResponse.user());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        audit.record("auth.portal_session", "session", null, principal.customerOrgId(), null,
                Map.of("username", principal.username()), "invitation accepted", false);
    }
}
