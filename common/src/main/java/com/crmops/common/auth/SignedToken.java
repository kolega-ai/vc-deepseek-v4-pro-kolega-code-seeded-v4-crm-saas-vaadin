package com.crmops.common.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Compact HMAC-SHA256 signed token used for two distinct purposes:
 * <ul>
 *   <li><b>Session tokens</b> — minted by the {@code decision} tier at login and presented by the
 *       {@code channel} tier with every authorization request. They carry the acting user's identity.</li>
 *   <li><b>Scope tokens</b> — minted by the {@code decision} tier for the {@code channel} tier to present to
 *       {@code core-mock}. They carry the bounded set of organization ids and the resource scope the call
 *       is allowed to touch.</li>
 * </ul>
 * The payload is opaque to every tier except the one that mints the token: identity, role and scope are
 * resolved from the database of the minting tier, never from client-supplied fields.
 */
public final class SignedToken {

    public static final String CLAIM_SUB = "sub";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_CUSTOMER_ORG = "customerOrgId";
    public static final String CLAIM_ORG_IDS = "orgIds";
    public static final String CLAIM_SCOPE = "scope";
    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_ISSUER = "iss";
    public static final String CLAIM_EXPIRES = "exp";
    public static final String CLAIM_JTI = "jti";

    public static final String TYPE_SESSION = "session";
    public static final String TYPE_SCOPE = "scope";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"crmops-token\"}";
    private static final String HEADER_B64 = base64Url(HEADER.getBytes(StandardCharsets.UTF_8));

    private final Map<String, Object> claims;

    private SignedToken(Map<String, Object> claims) {
        this.claims = claims;
    }

    public String subject() {
        return (String) claims.get(CLAIM_SUB);
    }

    public String username() {
        return (String) claims.get(CLAIM_USERNAME);
    }

    public String role() {
        return (String) claims.get(CLAIM_ROLE);
    }

    public Long customerOrgId() {
        Object v = claims.get(CLAIM_CUSTOMER_ORG);
        return v instanceof Number n ? n.longValue() : null;
    }

    public String type() {
        return (String) claims.get(CLAIM_TYPE);
    }

    public String scope() {
        return (String) claims.get(CLAIM_SCOPE);
    }

    @SuppressWarnings("unchecked")
    public List<Long> orgIds() {
        Object v = claims.get(CLAIM_ORG_IDS);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).toList();
    }

    public Instant expiresAt() {
        Object v = claims.get(CLAIM_EXPIRES);
        return v instanceof Number n ? Instant.ofEpochSecond(n.longValue()) : Instant.EPOCH;
    }

    public String claim(String key) {
        Object v = claims.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** Mints a token signed with the supplied secret and returns its compact representation. */
    public static String mint(Map<String, Object> claims, Duration ttl, String issuer, String secret) {
        Map<String, Object> payload = new HashMap<>(claims);
        payload.put(CLAIM_ISSUER, issuer);
        payload.put(CLAIM_EXPIRES, Instant.now().plus(ttl).getEpochSecond());
        payload.put(CLAIM_JTI, UUID.randomUUID().toString());
        String payloadB64;
        try {
            payloadB64 = base64Url(MAPPER.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new TokenException("failed to encode token payload", e);
        }
        String signingInput = HEADER_B64 + "." + payloadB64;
        return signingInput + "." + base64Url(hmac(signingInput, secret));
    }

    /**
     * Verifies the signature and expiry of a compact token and returns its claims.
     *
     * @throws TokenException if the token is malformed, tampered with, expired, or issued by the wrong issuer
     */
    public static SignedToken verify(String compact, String secret, String expectedIssuer, String expectedType) {
        if (compact == null || compact.isBlank()) {
            throw new TokenException("missing token");
        }
        String[] parts = compact.split("\\.");
        if (parts.length != 3) {
            throw new TokenException("malformed token");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmac(signingInput, secret);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new TokenException("malformed signature", e);
        }
        if (!constantTimeEquals(expected, actual)) {
            throw new TokenException("signature verification failed");
        }
        Map<String, Object> claims;
        try {
            claims = MAPPER.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new TokenException("malformed payload", e);
        }
        SignedToken token = new SignedToken(claims);
        if (expectedIssuer != null && !expectedIssuer.equals(token.claim(CLAIM_ISSUER))) {
            throw new TokenException("unexpected issuer");
        }
        if (expectedType != null && !expectedType.equals(token.type())) {
            throw new TokenException("unexpected token type");
        }
        if (token.expiresAt().isBefore(Instant.now())) {
            throw new TokenException("token expired");
        }
        return token;
    }

    private static byte[] hmac(String input, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new TokenException("HMAC unavailable", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
