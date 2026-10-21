package com.crmops.batch.web;

import com.crmops.batch.config.BatchProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Guards the manual job-trigger endpoints with the batch admin token. */
public class BatchAdminTokenFilter extends OncePerRequestFilter {

    private final BatchProperties properties;

    public BatchAdminTokenFilter(BatchProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/jobs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String presented = request.getHeader("X-Batch-Admin-Token");
        if (presented == null || !MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8),
                properties.batchAdminToken().getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"batch-auth-failed\",\"message\":\"batch admin token invalid\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
