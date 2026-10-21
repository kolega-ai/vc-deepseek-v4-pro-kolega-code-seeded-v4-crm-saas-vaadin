package com.crmops.channel.service;

import com.crmops.channel.domain.PasswordResetToken;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.repo.PasswordResetTokenRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.security.ActingPrincipal;
import com.crmops.channel.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Single-use, time-bound password reset. Tokens are random 256-bit values,
 * stored only as SHA-256 hashes, and expire after 30 minutes. Because email is
 * a local mock (outbox), the reset link is also surfaced in the UI so the
 * local demo flow stays completable.
 */
@Service
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final UserAccountRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final DecisionSyncService sync;
    private final AuditService audit;
    private final OutboxService outbox;
    private final com.crmops.channel.decision.DecisionClient decisionClient;
    private final String baseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserAccountRepository userRepo, PasswordResetTokenRepository tokenRepo,
                                PasswordEncoder passwordEncoder, DecisionSyncService sync, AuditService audit,
                                OutboxService outbox, com.crmops.channel.decision.DecisionClient decisionClient,
                                com.crmops.channel.config.ChannelProperties properties) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.sync = sync;
        this.audit = audit;
        this.outbox = outbox;
        this.decisionClient = decisionClient;
        this.baseUrl = properties.baseUrl();
    }

    /** Returns the plaintext reset link (mock email) or null when the user is unknown. */
    @Transactional
    public String requestReset(String email) {
        UserAccount user = userRepo.findByEmailIgnoreCase(email == null ? "" : email.trim()).orElse(null);
        if (user == null || !user.isEnabled()) {
            return null;
        }
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = Instant.now();
        tokenRepo.save(new PasswordResetToken(user.getId(), sha256(token), now.plus(TOKEN_TTL), now));
        String link = baseUrl + "/reset-password?token=" + token;
        outbox.enqueue(user.getEmail(), user.getId(), "Reset your CrmOps password",
                "Hello " + user.getDisplayName() + ",\n\nUse this link to set a new password (valid for 30 minutes):\n"
                        + link + "\n\nIf you did not request this, you can ignore this message.",
                "password-reset");
        return link;
    }

    /** Validates the token, checks expiry and single use, then sets the password. */
    @Transactional
    public void completeReset(String token, String newPassword) {
        UserService.validatePassword(newPassword);
        PasswordResetToken stored = tokenRepo.findByTokenHash(sha256(token))
                .orElseThrow(() -> new ValidationException("invalid or expired reset link"));
        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("invalid or expired reset link");
        }
        UserAccount user = userRepo.findById(stored.getUserId())
                .orElseThrow(() -> new ValidationException("account no longer exists"));
        if (!user.isEnabled()) {
            throw new ValidationException("account is disabled");
        }
        stored.markUsed(Instant.now());
        sync.syncUserWithPassword(user, passwordEncoder.encode(newPassword));
        audit.record("user.password_reset", "user", user.getId(), user.getCustomerOrgId(), null, null,
                "self-service: single-use token", false);
    }

    /** Self-service password change: the current password is verified against the decision tier. */
    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        UserAccount user = userRepo.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("user", principal.userId()));
        UserService.validatePassword(newPassword);
        // The current password is checked by the decision tier (the credential
        // verifier) via a normal login round-trip; the resulting session token
        // is discarded.
        decisionClient.login(user.getUsername(), currentPassword);
        sync.syncUserWithPassword(user, passwordEncoder.encode(newPassword));
        audit.record("user.password_change", "user", user.getId(), user.getCustomerOrgId(), null, null,
                "self-service: current password verified by decision", false);
        outbox.enqueue(user.getEmail(), user.getId(), "Your CrmOps password changed",
                "Hello " + user.getDisplayName() + ", your CrmOps password was changed. If this was not you, contact support immediately.",
                "password-change");
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
