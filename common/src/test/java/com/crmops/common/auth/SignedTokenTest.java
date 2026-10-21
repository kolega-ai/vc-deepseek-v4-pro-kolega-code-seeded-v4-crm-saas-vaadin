package com.crmops.common.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedTokenTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac";

    @Test
    void roundTripPreservesClaims() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_USERNAME, "sarah",
                        SignedToken.CLAIM_ROLE, "ACCOUNT_MANAGER",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION),
                Duration.ofMinutes(5), "decision", SECRET);
        SignedToken token = SignedToken.verify(compact, SECRET, "decision", SignedToken.TYPE_SESSION);
        assertEquals("42", token.subject());
        assertEquals("sarah", token.username());
        assertEquals("ACCOUNT_MANAGER", token.role());
        assertNotNull(token.expiresAt());
    }

    @Test
    void tamperedTokenIsRejected() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION),
                Duration.ofMinutes(5), "decision", SECRET);
        String[] parts = compact.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("a") ? "b" : "a") + parts[2].substring(1);
        assertThrows(TokenException.class,
                () -> SignedToken.verify(tampered, SECRET, "decision", SignedToken.TYPE_SESSION));
    }

    @Test
    void wrongSecretIsRejected() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION),
                Duration.ofMinutes(5), "decision", SECRET);
        assertThrows(TokenException.class,
                () -> SignedToken.verify(compact, "some-other-secret", "decision", SignedToken.TYPE_SESSION));
    }

    @Test
    void wrongIssuerIsRejected() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION),
                Duration.ofMinutes(5), "decision", SECRET);
        assertThrows(TokenException.class,
                () -> SignedToken.verify(compact, SECRET, "someone-else", SignedToken.TYPE_SESSION));
    }

    @Test
    void wrongTypeIsRejected() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION),
                Duration.ofMinutes(5), "decision", SECRET);
        assertThrows(TokenException.class,
                () -> SignedToken.verify(compact, SECRET, "decision", SignedToken.TYPE_SCOPE));
    }

    @Test
    void expiredTokenIsRejected() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SESSION),
                Duration.ofSeconds(-1), "decision", SECRET);
        assertThrows(TokenException.class,
                () -> SignedToken.verify(compact, SECRET, "decision", SignedToken.TYPE_SESSION));
    }

    @Test
    void scopeTokenCarriesOrgClaim() {
        String compact = SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SCOPE,
                        SignedToken.CLAIM_SCOPE, "invoices.read",
                        SignedToken.CLAIM_ORG_IDS, java.util.List.of(1L, 3L)),
                Duration.ofMinutes(5), "decision", SECRET);
        SignedToken token = SignedToken.verify(compact, SECRET, "decision", SignedToken.TYPE_SCOPE);
        assertTrue(token.orgIds().contains(1L));
        assertTrue(token.orgIds().contains(3L));
        assertEquals("invoices.read", token.scope());
    }
}
