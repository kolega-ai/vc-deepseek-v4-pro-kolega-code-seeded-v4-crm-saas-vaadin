package com.crmops.decision.security;

import com.crmops.decision.config.DecisionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates service-to-service calls (login handoff, snapshot sync,
 * batch queries) with a shared channel-service token. Comparison is
 * constant-time via digest equality to avoid timing side channels.
 */
public class ServiceTokenFilter extends OncePerRequestFilter {

    private final DecisionProperties properties;
    private final RequestMatcher pathMatcher;

    public ServiceTokenFilter(DecisionProperties properties, RequestMatcher pathMatcher) {
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
        String presented = request.getHeader("X-Service-Token");
        if (presented == null || !safeEquals(presented, properties.channelServiceToken())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"service-auth-failed\",\"message\":\"service credential missing or invalid\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean safeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
