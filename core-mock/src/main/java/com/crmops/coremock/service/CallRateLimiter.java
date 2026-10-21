package com.crmops.coremock.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window per-credential rate limit for the mock vendor core. Real vendor
 * cores throttle callers; this keeps the local mock honest about the behavior.
 */
@Component
public class CallRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerMinute;

    public CallRateLimiter(com.crmops.coremock.config.CoreProperties properties) {
        this.limitPerMinute = properties.rateLimitPerMinute();
    }

    public boolean allow(String credentialId) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(credentialId, (k, w) -> {
            if (w == null || w.minute != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, w.count + 1);
        });
        return window.count <= limitPerMinute;
    }

    private record Window(long minute, int count) {
    }
}
