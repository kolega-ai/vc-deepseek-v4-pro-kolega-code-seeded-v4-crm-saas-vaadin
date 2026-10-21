package com.crmops.coremock.security;

import com.crmops.common.auth.SignedToken;
import com.crmops.coremock.config.CoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Verifies the decision-minted scope token presented on data endpoints and
 * stores the granted scope as a request attribute. A scope token is the ONLY
 * thing that grants access to org data here: raw org ids in the URL are
 * validated against the token's orgIds claim by the controller.
 */
public class ScopeTokenFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "crmops.scopeToken";

    private final CoreProperties properties;

    public ScopeTokenFilter(CoreProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            CoreServiceKeyFilter.reject(response, HttpServletResponse.SC_UNAUTHORIZED, "core-1002",
                    "scope token missing or invalid");
            return;
        }
        try {
            SignedToken token = SignedToken.verify(header.substring(7),
                    properties.scopeTokenSecret(), "decision", SignedToken.TYPE_SCOPE);
            request.setAttribute(REQUEST_ATTRIBUTE, token);
            chain.doFilter(request, response);
        } catch (RuntimeException e) {
            CoreServiceKeyFilter.reject(response, HttpServletResponse.SC_UNAUTHORIZED, "core-1002",
                    "scope token missing or invalid");
        }
    }
}
