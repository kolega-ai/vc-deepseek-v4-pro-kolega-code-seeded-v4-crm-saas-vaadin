package com.crmops.channel.service;

import com.crmops.common.api.Dto.UserInfo;
import com.crmops.channel.domain.AssignmentRelation;
import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.OrgAssignment;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.repo.OrgAssignmentRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.ActingPrincipal;
import com.crmops.channel.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.crmops.common.core.Actions.USER_ADMIN;

/**
 * User administration. Role changes, account enable/disable and org
 * assignments are platform-admin operations (decision-enforced); every change
 * is audited and pushed to the decision tier. There is deliberately no
 * impersonation path: support for a locked-out account goes through password
 * reset and re-enablement, and the audit trail always names the real actor.
 */
@Service
public class UserService {

    private final UserAccountRepository userRepo;
    private final OrgAssignmentRepository assignmentRepo;
    private final ContactRepository contactRepo;
    private final PasswordEncoder passwordEncoder;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final DecisionSyncService sync;
    private final OutboxService outbox;

    public UserService(UserAccountRepository userRepo, OrgAssignmentRepository assignmentRepo,
                       ContactRepository contactRepo, PasswordEncoder passwordEncoder, CrmAuthorization authz,
                       AuditService audit, DecisionSyncService sync, OutboxService outbox) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.contactRepo = contactRepo;
        this.passwordEncoder = passwordEncoder;
        this.authz = authz;
        this.audit = audit;
        this.sync = sync;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public UserAccount get(long userId) {
        return userRepo.findById(userId).orElseThrow(() -> new NotFoundException("user", userId));
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> search(String search, Role role, Boolean enabled, Pageable pageable) {
        authz.require(USER_ADMIN);
        return userRepo.findAll(UserQuerySupport.filter(search, role, enabled), pageable);
    }

    /** Limited staff directory (assignee dropdowns, owner pickers). */
    @Transactional(readOnly = true)
    public List<UserAccount> staffDirectory() {
        ActingPrincipal principal = SecurityUtils.requireUser();
        if (principal.role() == Role.CUSTOMER_CONTACT) {
            throw new org.springframework.security.access.AccessDeniedException("customers cannot list staff");
        }
        return userRepo.findAll().stream()
                .filter(u -> u.getRole() != Role.CUSTOMER_CONTACT)
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .toList();
    }

    /** People who can be pulled into an escalation thread. */
    @Transactional(readOnly = true)
    public List<UserAccount> threadParticipants() {
        SecurityUtils.requireUser();
        return userRepo.findAll().stream()
                .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
                .toList();
    }

    /** Creates a user with an initial password (staff) — admin only. */
    @Transactional
    public UserAccount createUser(String username, String displayName, String email, Role role,
                                  Long customerOrgId, String title, String phone, String plainPassword) {
        authz.require(USER_ADMIN);
        if (role == Role.PLATFORM_ADMIN && !SecurityUtils.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("only platform admins can create admins");
        }
        String trimmedUser = username.trim().toLowerCase();
        if (userRepo.existsByUsernameIgnoreCase(trimmedUser) || userRepo.existsByEmailIgnoreCase(email.trim())) {
            throw new ValidationException("username or email already in use");
        }
        validatePassword(plainPassword);
        Instant now = Instant.now();
        UserAccount user = new UserAccount(nextId(), trimmedUser, displayName.trim(), email.trim(), role,
                true, customerOrgId, title, phone, now);
        userRepo.save(user);
        sync.syncUserWithPassword(user, passwordEncoder.encode(plainPassword));
        audit.record("user.create", "user", user.getId(), customerOrgId, null,
                Map.of("username", user.getUsername(), "role", role.name()),
                authz.require(USER_ADMIN).authorizationBasis(), false);
        outbox.enqueue(email.trim(), user.getId(), "Welcome to CrmOps",
                "Hello " + displayName + ", your CrmOps account has been created. Your username is " + user.getUsername() + ".", "welcome");
        return user;
    }

    @Transactional
    public UserAccount updateUserProfile(long userId, String displayName, String title, String phone, String email) {
        UserAccount user = get(userId);
        if (java.util.Objects.equals(SecurityUtils.current() == null ? null : SecurityUtils.current().userId(), userId)) {
            updateOwnProfile(displayName, title, phone);
            if (email != null && !email.isBlank() && !email.trim().equalsIgnoreCase(user.getEmail())) {
                if (userRepo.existsByEmailIgnoreCase(email.trim())) {
                    throw new ValidationException("email already in use");
                }
                user.changeEmail(email.trim(), Instant.now());
                sync.fullSync();
                audit.record("user.email_change", "user", userId, user.getCustomerOrgId(), null,
                        Map.of("email", email.trim()), "self-service", false);
            }
            return user;
        }
        authz.require(USER_ADMIN);
        user.applyProfile(displayName.trim(), title, phone, Instant.now());
        sync.fullSync();
        audit.record("user.profile_update", "user", userId, user.getCustomerOrgId(), null,
                Map.of("displayName", user.getDisplayName()), authz.require(USER_ADMIN).authorizationBasis(), false);
        return user;
    }

    @Transactional
    public UserAccount updateOwnProfile(String displayName, String title, String phone) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        UserAccount user = get(principal.userId());
        user.applyProfile(displayName.trim(), title, phone, Instant.now());
        sync.fullSync();
        audit.record("user.profile_update", "user", user.getId(), user.getCustomerOrgId(), null,
                Map.of("displayName", user.getDisplayName()), "self-service", false);
        return user;
    }

    @Transactional
    public UserAccount changeRole(long userId, Role newRole) {
        var verdict = authz.require(USER_ADMIN);
        UserAccount user = get(userId);
        if (user.getRole() == Role.PLATFORM_ADMIN && newRole != Role.PLATFORM_ADMIN) {
            long admins = userRepo.findByRoleOrderByDisplayNameAsc(Role.PLATFORM_ADMIN).size();
            if (admins <= 1) {
                throw new ValidationException("cannot demote the last platform admin");
            }
        }
        Role before = user.getRole();
        user.changeRole(newRole, Instant.now());
        if (newRole == Role.CUSTOMER_CONTACT && user.getCustomerOrgId() == null) {
            throw new ValidationException("customer contacts need an organization");
        }
        sync.fullSync();
        audit.record("user.role_change", "user", userId, user.getCustomerOrgId(),
                Map.of("role", before.name()), Map.of("role", newRole.name()),
                verdict.authorizationBasis(), false);
        return user;
    }

    @Transactional
    public UserAccount setEnabled(long userId, boolean enabled) {
        var verdict = authz.require(USER_ADMIN);
        UserAccount user = get(userId);
        if (user.getId().equals(SecurityUtils.requireUser().userId()) && !enabled) {
            throw new ValidationException("you cannot disable your own account");
        }
        user.setEnabled(enabled, Instant.now());
        sync.fullSync();
        audit.record(enabled ? "user.enable" : "user.disable", "user", userId, user.getCustomerOrgId(),
                Map.of("enabled", !enabled), Map.of("enabled", enabled), verdict.authorizationBasis(), false);
        return user;
    }

    @Transactional
    public void assignOrg(long userId, long orgId, AssignmentRelation relation) {
        var verdict = authz.require(USER_ADMIN);
        UserAccount user = get(userId);
        if (user.getRole() == Role.CUSTOMER_CONTACT) {
            throw new ValidationException("customer contacts are scoped to their own organization");
        }
        assignmentRepo.findByUserIdAndOrgId(userId, orgId)
                .ifPresentOrElse(
                        existing -> existing.setRelation(relation),
                        () -> assignmentRepo.save(new OrgAssignment(userId, orgId, relation)));
        sync.fullSync();
        audit.record("user.org_assign", "assignment", null, orgId,
                null, Map.of("userId", userId, "orgId", orgId, "relation", relation.name()),
                verdict.authorizationBasis(), false);
    }

    @Transactional
    public void removeAssignment(long userId, long orgId) {
        var verdict = authz.require(USER_ADMIN);
        assignmentRepo.findByUserIdAndOrgId(userId, orgId)
                .ifPresent(assignment -> {
                    assignmentRepo.delete(assignment);
                    sync.fullSync();
                    audit.record("user.org_unassign", "assignment", null, orgId,
                            Map.of("userId", userId, "orgId", orgId), null, verdict.authorizationBasis(), false);
                });
    }

    @Transactional(readOnly = true)
    public List<OrgAssignment> assignments(long userId) {
        return assignmentRepo.findByUserId(userId);
    }

    /** Admin-initiated password reset (no impersonation). */
    @Transactional
    public void adminResetPassword(long userId, String newPassword) {
        var verdict = authz.require(USER_ADMIN);
        UserAccount user = get(userId);
        validatePassword(newPassword);
        sync.syncUserWithPassword(user, passwordEncoder.encode(newPassword));
        audit.record("user.password_admin_reset", "user", userId, user.getCustomerOrgId(), null, null,
                verdict.authorizationBasis(), false);
        outbox.enqueue(user.getEmail(), userId, "Your CrmOps password was reset",
                "Hello " + user.getDisplayName() + ", an administrator reset your password. Please use the new credentials provided to you.", "password-admin-reset");
    }

    /** Keep the channel mirror in sync with the decision tier after a login. */
    @Transactional
    public UserAccount ensureMirror(UserInfo info) {
        return userRepo.findById(info.id())
                .map(existing -> {
                    existing.applyProfile(info.displayName(), existing.getTitle(), existing.getPhone(), Instant.now());
                    existing.changeEmail(info.email(), Instant.now());
                    existing.changeRole(Role.fromExternal(info.role()), Instant.now());
                    existing.setEnabled(info.enabled(), Instant.now());
                    existing.setCustomerOrgId(info.customerOrgId(), Instant.now());
                    return existing;
                })
                .orElseGet(() -> userRepo.save(new UserAccount(info.id(), info.username(), info.displayName(),
                        info.email(), Role.fromExternal(info.role()), info.enabled(), info.customerOrgId(),
                        null, null, Instant.now())));
    }

    /** All users eligible to receive invites — staff only. */
    @Transactional(readOnly = true)
    public List<UserAccount> staffByRole(Role role) {
        SecurityUtils.requireUser();
        return userRepo.findByRoleOrderByDisplayNameAsc(role);
    }

    /** Batch display-name resolution for list views. */
    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(java.util.Collection<Long> ids) {
        return userRepo.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));
    }

    @Transactional(readOnly = true)
    public Contact contactOf(long orgId, long userId) {
        return contactRepo.findByUserId(userId).stream()
                .filter(c -> c.getOrg().getId().equals(orgId))
                .findFirst()
                .orElse(null);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new ValidationException("password must be 12-128 characters");
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLower || !hasUpper || !hasDigit) {
            throw new ValidationException("password needs upper- and lower-case letters and a digit");
        }
    }

    /** Internal user id generation; the decision tier keeps the same id. */
    private static final AtomicLong ID_SEQ = new AtomicLong(1_000_000_000L);

    private static long nextId() {
        return ID_SEQ.incrementAndGet();
    }
}
