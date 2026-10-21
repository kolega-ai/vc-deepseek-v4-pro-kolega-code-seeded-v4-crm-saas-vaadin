package com.crmops.channel.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-window rate limit for API-key requests (per key, 120/min). */
@Component
public class ApiKeyRateLimiter {

    private static final int LIMIT_PER_MINUTE = 120;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String keyHash) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(keyHash, (k, w) -> {
            if (w == null || w.minute != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, w.count + 1);
        });
        return window.count <= LIMIT_PER_MINUTE;
    }

    private record Window(long minute, int count) {
    }
}
