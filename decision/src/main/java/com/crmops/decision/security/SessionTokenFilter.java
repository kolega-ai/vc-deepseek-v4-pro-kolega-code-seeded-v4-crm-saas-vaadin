package com.crmops.decision.security;

import com.crmops.common.auth.SignedToken;
import com.crmops.decision.config.DecisionProperties;
import com.crmops.decision.domain.AuthUser;
import com.crmops.decision.repo.AuthUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Resolves the Bearer session token on authorization endpoints. The token
 * signature is verified, then the acting user is re-loaded from the database:
 * a disabled account or missing user is rejected even with a valid signature.
 */
public class SessionTokenFilter extends OncePerRequestFilter {

    private final AuthUserRepository userRepo;
    private final DecisionProperties properties;
    private final RequestMatcher pathMatcher;

    public SessionTokenFilter(AuthUserRepository userRepo, DecisionProperties properties, RequestMatcher pathMatcher) {
        this.userRepo = userRepo;
        this.properties = properties;
        this.pathMatcher = pathMatcher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, "missing bearer token");
            return;
        }
        try {
            SignedToken token = SignedToken.verify(header.substring(7),
                    properties.sessionTokenSecret(), "decision", SignedToken.TYPE_SESSION);
            AuthUser user = userRepo.findById(Long.parseLong(token.subject())).orElse(null);
            if (user == null || !user.isEnabled()) {
                reject(response, "account unavailable");
                return;
            }
            CrmPrincipal principal = new CrmPrincipal(user.getId(), user.getUsername(), user.getRole(),
                    user.getCustomerOrgId());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (RuntimeException e) {
            reject(response, "invalid token");
        }
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"auth-failed\",\"message\":\"" + reason + "\"}");
    }
}
