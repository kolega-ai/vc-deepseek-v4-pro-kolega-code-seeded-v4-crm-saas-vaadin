package com.crmops.coremock.security;

import com.crmops.common.auth.SignedToken;
import com.crmops.coremock.config.CoreProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Enforces the vendor-core service credential (X-Core-Service-Key). The
 * credential is held by the channel tier; comparison is constant-time.
 */
public class CoreServiceKeyFilter extends OncePerRequestFilter {

    private final CoreProperties properties;

    public CoreServiceKeyFilter(CoreProperties properties) {
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
        String presented = request.getHeader("X-Core-Service-Key");
        if (presented == null || !safeEquals(presented, properties.coreServiceKey())) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "core-1001",
                    "service credential missing or invalid");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean safeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
