package com.crmops.channel.service;

import com.crmops.channel.domain.InviteToken;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.repo.InviteTokenRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.SecurityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static com.crmops.common.core.Actions.CONTACT_INVITE;

/**
 * Invite-based onboarding for customer contacts. Staff (account managers and
 * admins) invite a contact email; the invite creates a disabled customer
 * account plus a single-use token; accepting the invite sets the password and
 * activates the account. All state changes are audited and synced to decision.
 */
@Service
public class InviteService {

    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final InviteTokenRepository inviteRepo;
    private final UserAccountRepository userRepo;
    private final ContactRepository contactRepo;
    private final PasswordEncoder passwordEncoder;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final DecisionSyncService sync;
    private final OutboxService outbox;
    private final String baseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public InviteService(InviteTokenRepository inviteRepo, UserAccountRepository userRepo,
                         ContactRepository contactRepo, PasswordEncoder passwordEncoder, CrmAuthorization authz,
                         AuditService audit, DecisionSyncService sync, OutboxService outbox,
                         com.crmops.channel.config.ChannelProperties properties) {
        this.inviteRepo = inviteRepo;
        this.userRepo = userRepo;
        this.contactRepo = contactRepo;
        this.passwordEncoder = passwordEncoder;
        this.authz = authz;
        this.audit = audit;
        this.sync = sync;
        this.outbox = outbox;
        this.baseUrl = properties.baseUrl();
    }

    /** Invites a contact of an organization to get a portal login. Returns the invite link (mock email). */
    @Transactional
    public String invite(long orgId, long contactId) {
        var verdict = authz.require(CONTACT_INVITE, orgId);
        var contact = contactRepo.findById(contactId)
                .filter(c -> c.getOrg().getId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("contact", contactId));
        if (contact.getUserId() != null) {
            throw new ValidationException("this contact already has a portal account");
        }
        if (userRepo.existsByEmailIgnoreCase(contact.getEmail())) {
            throw new ValidationException("an account with this email already exists");
        }
        Instant now = Instant.now();
        // A disabled account is created up front so the invite can always be
        // revoked cleanly by disabling the account.
        UserAccount user = new UserAccount(nextUserId(), contact.getEmail().toLowerCase(),
                contact.fullName(), contact.getEmail(), Role.CUSTOMER_CONTACT, false, orgId,
                contact.getTitle(), contact.getPhone(), now);
        userRepo.save(user);
        contact.linkUser(user.getId(), now);
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        inviteRepo.save(new InviteToken(contact.getEmail(), orgId, user.getId(),
                PasswordResetService.sha256(token), now.plus(INVITE_TTL), SecurityUtils.requireUser().userId(), now));
        String link = baseUrl + "/invite?token=" + token;
        outbox.enqueue(contact.getEmail(), user.getId(), "You're invited to the CrmOps customer portal",
                "Hello " + contact.fullName() + ",\n\nAccept your invitation and set a password here (valid 7 days):\n"
                        + link,
                "invite");
        audit.record("user.invite_sent", "user", user.getId(), orgId, null,
                Map.of("email", contact.getEmail()), verdict.authorizationBasis(), false);
        return link;
    }

    @Transactional
    public UserAccount accept(String token, String contactEmail, String newPassword) {
        UserService.validatePassword(newPassword);
        InviteToken invite = inviteRepo.findByTokenHash(PasswordResetService.sha256(token))
                .orElseThrow(() -> new ValidationException("invalid or expired invitation"));
        if (invite.getUsedAt() != null || invite.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("invalid or expired invitation");
        }
        // Contacts frequently accept with the work address they already use
        // rather than the one the invitation was addressed to.
        UserAccount user = contactEmail == null || contactEmail.isBlank()
                ? userRepo.findById(invite.getUserId())
                        .orElseThrow(() -> new ValidationException("account no longer exists"))
                : userRepo.findByEmailIgnoreCase(contactEmail.trim())
                        .orElseGet(() -> userRepo.findById(invite.getUserId())
                                .orElseThrow(() -> new ValidationException("account no longer exists")));
        invite.markUsed(Instant.now());
        user.setEnabled(true, Instant.now());
        user.setCustomerOrgId(invite.getOrgId(), Instant.now());
        sync.syncUserWithPassword(user, passwordEncoder.encode(newPassword));
        audit.record("user.invite_accepted", "user", user.getId(), invite.getOrgId(), null, null,
                "self-service: single-use invite token", false);
        return user;
    }

    private static final java.util.concurrent.atomic.AtomicLong INVITE_USER_IDS =
            new java.util.concurrent.atomic.AtomicLong(2_000_000_000L);

    private static long nextUserId() {
        return INVITE_USER_IDS.incrementAndGet();
    }
}
