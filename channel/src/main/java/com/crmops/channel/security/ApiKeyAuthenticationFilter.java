package com.crmops.channel.security;

import com.crmops.channel.service.ApiKeyRateLimiter;
import com.crmops.channel.service.ApiKeyService;
import com.crmops.channel.domain.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates /api/v1 requests with an organization-scoped API key. Only the
 * SHA-256 hash is stored, so the presented key is hashed and looked up. The
 * key must be ACTIVE and unexpired in the channel database; the decision tier
 * re-verifies the key record on every action authorization as defense in depth.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger accessLog = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyService apiKeyService;
    private final ApiKeyRateLimiter rateLimiter;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ApiKeyRateLimiter rateLimiter) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = request.getHeader("X-Api-Key");
        if (rawKey == null || rawKey.isBlank()) {
            reject(response, 401, "api-key-required", "provide an API key in the X-Api-Key header");
            return;
        }
        if (!rateLimiter.allow(ApiKeyService.sha256(rawKey))) {
            reject(response, 429, "api-rate-limited", "too many requests; slow down");
            return;
        }
        ApiKey key = apiKeyService.resolve(rawKey);
        if (key == null) {
            reject(response, 401, "api-key-invalid", "API key is invalid, revoked or expired");
            return;
        }
        apiKeyService.recordUse(key.getId(), request.getRequestURI());
        accessLog.info("api call " + request.getRequestURI() + " key=" + key.getName()
                + " client=" + request.getHeader("User-Agent"));
        var authentication = new UsernamePasswordAuthenticationToken(
                new ApiKeyPrincipal(key.getId(), key.getName(), key.getOrg().getId(), key.getKeyHash()), null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_CONTACT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
