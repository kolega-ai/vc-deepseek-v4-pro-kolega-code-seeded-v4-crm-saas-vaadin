package com.crmops.common.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory brute-force guard: five consecutive failures per
 * (username, client-address) pair lock the pair out for fifteen minutes.
 * Local-process only; behind a load balancer this would live in shared state.
 * Both the channel tier (login form) and the decision tier (credential check)
 * run their own instance.
 */
public final class LoginAttemptGuard {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Entry> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String username, String clientAddress) {
        Entry entry = attempts.get(key(username, clientAddress));
        return entry != null && entry.lockedUntil.isAfter(Instant.now());
    }

    public void recordFailure(String username, String clientAddress) {
        attempts.compute(key(username, clientAddress), (k, entry) -> {
            if (entry == null) {
                return new Entry(1, Instant.EPOCH);
            }
            int failures = entry.failures + 1;
            Instant lockedUntil = failures >= MAX_FAILURES
                    ? Instant.now().plus(LOCK_DURATION)
                    : entry.lockedUntil;
            return new Entry(failures, lockedUntil);
        });
    }

    public void recordSuccess(String username, String clientAddress) {
        attempts.remove(key(username, clientAddress));
    }

    private static String key(String username, String clientAddress) {
        return (username == null ? "" : username.toLowerCase()) + "|" + clientAddress;
    }

    private record Entry(int failures, Instant lockedUntil) {
    }
}
