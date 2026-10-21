package com.crmops.channel.service;

import com.crmops.channel.domain.CommentVisibility;
import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.EscalationComment;
import com.crmops.channel.domain.EscalationStatus;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.Severity;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.repo.EscalationCommentRepository;
import com.crmops.channel.repo.EscalationRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.ActingPrincipal;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.workflow.EscalationStatusMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.ESCALATION_ASSIGN;
import static com.crmops.common.core.Actions.ESCALATION_CREATE;
import static com.crmops.common.core.Actions.ESCALATION_INTERNAL_NOTE;
import static com.crmops.common.core.Actions.ESCALATION_READ;
import static com.crmops.common.core.Actions.ESCALATION_UPDATE;

/**
 * Support escalation workflow. Status changes follow the explicit
 * EscalationStatusMachine edge list. Internal comments are never materialized
 * into any view, export or payload delivered to a customer: every read path
 * filters by the acting role server-side.
 */
@Service
public class EscalationService {

    private final EscalationRepository escalationRepo;
    private final EscalationCommentRepository commentRepo;
    private final OrganizationRepository orgRepo;
    private final UserAccountRepository userRepo;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final WebhookService webhookService;
    private final NotificationService notifications;

    public EscalationService(EscalationRepository escalationRepo, EscalationCommentRepository commentRepo,
                             OrganizationRepository orgRepo, UserAccountRepository userRepo,
                             CrmAuthorization authz, AuditService audit, WebhookService webhookService,
                             NotificationService notifications) {
        this.escalationRepo = escalationRepo;
        this.commentRepo = commentRepo;
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
        this.authz = authz;
        this.audit = audit;
        this.webhookService = webhookService;
        this.notifications = notifications;
    }

    public record EscalationView(long id, long orgId, String orgName, String subject, String description,
                                 String severity, String status, Long assigneeId, String assigneeName,
                                 Instant createdAt, Instant updatedAt, Instant slaDueAt, boolean slaBreached,
                                 Instant resolvedAt) {
    }

    public record CommentView(long id, Long authorId, String authorName, String authorRole, String visibility,
                              String body, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<Escalation> listByOrg(long orgId) {
        authz.require(ESCALATION_READ, orgId);
        return escalationRepo.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    /**
     * The escalation queue. Support agents see the whole team queue; account
     * managers see escalations of their assigned organizations; customer
     * contacts see their own organization's escalations (customer-visible
     * projection only).
     */
    @Transactional(readOnly = true)
    public List<EscalationView> queue(EscalationStatus status, Severity severity, Long assigneeId, int limit) {
        List<Long> scope = authz.orgScope(ESCALATION_READ);
        ActingPrincipal principal = SecurityUtils.requireUser();
        List<Escalation> candidates = scope == null
                ? escalationRepo.findAll()
                : escalationRepo.findByOrgIdInOrderByCreatedAtDesc(scope);
        if (status == null) {
            // No status filter = the active queue (open / waiting states).
            candidates = candidates.stream().filter(e -> activeStatuses().contains(e.getStatus())).toList();
        }
        return candidates.stream()
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> severity == null || e.getSeverity() == severity)
                .filter(e -> assigneeId == null || assigneeId.equals(e.getAssigneeId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(Math.min(Math.max(limit, 1), 500))
                .map(e -> toView(e, principal.role()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Escalation get(long escalationId) {
        Escalation escalation = escalationRepo.findById(escalationId)
                .orElseThrow(() -> new NotFoundException("escalation", escalationId));
        authz.require(ESCALATION_READ, escalation.getOrg().getId());
        return escalation;
    }

    @Transactional(readOnly = true)
    public EscalationView view(long escalationId) {
        Escalation escalation = get(escalationId);
        return toView(escalation, SecurityUtils.requireUser().role());
    }

    @Transactional
    public Escalation create(long orgId, String subject, String description, Severity severity) {
        var verdict = authz.require(ESCALATION_CREATE, orgId);
        validate(subject, description);
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        Escalation escalation = new Escalation(org, subject.trim(), description.trim(), severity,
                EscalationStatus.OPEN, null, SecurityUtils.requireUser().userId(), Instant.now());
        escalationRepo.save(escalation);
        audit.record("escalation.create", "escalation", escalation.getId(), orgId, null,
                Map.of("subject", escalation.getSubject(), "severity", severity.name()),
                verdict.authorizationBasis(), true);
        webhookService.enqueueForOrg(orgId, "escalation.created", Map.of(
                "escalationId", escalation.getId(), "orgId", orgId, "subject", escalation.getSubject(),
                "severity", severity.name()));
        notifySupportTeam("New escalation: " + escalation.getSubject(),
                org.getName() + " · " + severity.label(),
                "escalations/" + escalation.getId());
        return escalation;
    }

    @Transactional
    public Escalation assign(long escalationId, Long assigneeId) {
        Escalation escalation = get(escalationId);
        var verdict = authz.require(ESCALATION_ASSIGN, escalation.getOrg().getId());
        if (assigneeId != null) {
            UserAccount assignee = userRepo.findById(assigneeId)
                    .orElseThrow(() -> new ValidationException("unknown assignee"));
            if (assignee.getRole() != Role.SUPPORT_AGENT && assignee.getRole() != Role.PLATFORM_ADMIN) {
                throw new ValidationException("only support agents can be assigned escalations");
            }
        }
        Long before = escalation.getAssigneeId();
        escalation.assign(assigneeId, Instant.now());
        audit.record("escalation.assign", "escalation", escalationId, escalation.getOrg().getId(),
                Map.of("assigneeId", before), Map.of("assigneeId", assigneeId),
                verdict.authorizationBasis(), false);
        webhookService.enqueueForOrg(escalation.getOrg().getId(), "escalation.updated", Map.of(
                "escalationId", escalationId, "event", "assigned"));
        if (assigneeId != null) {
            notifications.notify(assigneeId, "escalation", "Escalation assigned to you",
                    escalation.getSubject() + " (" + escalation.getOrg().getName() + ")",
                    "escalations/" + escalationId);
        }
        return escalation;
    }

    /** Status transition via the explicit edge list. */
    @Transactional
    public Escalation transition(long escalationId, String action) {
        Escalation escalation = get(escalationId);
        var verdict = authz.require(ESCALATION_UPDATE, escalation.getOrg().getId());
        ActingPrincipal principal = SecurityUtils.requireUser();
        // Customers may move the workflow only by replying (customer_replied);
        // resolve/close/reopen are staff actions.
        if (principal.role() == Role.CUSTOMER_CONTACT
                && !EscalationStatusMachine.ACTION_CUSTOMER_REPLIED.equals(action)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "customer contacts can only reply to escalations");
        }
        if (!EscalationStatusMachine.isAllowed(escalation.getStatus(), action)) {
            throw new ValidationException("cannot '" + action + "' an escalation in status "
                    + escalation.getStatus().label());
        }
        EscalationStatus before = escalation.getStatus();
        EscalationStatus target = EscalationStatusMachine.target(escalation.getStatus(), action);
        escalation.moveTo(target, Instant.now());
        audit.record("escalation.status_change", "escalation", escalationId, escalation.getOrg().getId(),
                Map.of("status", before.name()), Map.of("status", target.name()),
                verdict.authorizationBasis(), true);
        if (target == EscalationStatus.RESOLVED) {
            webhookService.enqueueForOrg(escalation.getOrg().getId(), "escalation.resolved", Map.of(
                    "escalationId", escalationId, "orgId", escalation.getOrg().getId(),
                    "subject", escalation.getSubject()));
            notifyOrgOwner(escalation, "Escalation resolved", escalation.getSubject());
        }
        return escalation;
    }

    /** Re-opens an escalation when new information arrives on a settled case. */
    @Transactional
    public Escalation reopen(long escalationId) {
        Escalation escalation = get(escalationId);
        var verdict = authz.require(ESCALATION_UPDATE, escalation.getOrg().getId());
        EscalationStatus before = escalation.getStatus();
        escalation.moveTo(EscalationStatus.OPEN, Instant.now());
        audit.record("escalation.reopen", "escalation", escalationId, escalation.getOrg().getId(),
                Map.of("status", before.name()), Map.of("status", EscalationStatus.OPEN.name()),
                verdict.authorizationBasis(), true);
        webhookService.enqueueForOrg(escalation.getOrg().getId(), "escalation.updated", Map.of(
                "escalationId", escalationId, "event", "reopened"));
        return escalation;
    }

    /** Internal-only note; never visible to customers, never exported to them. */
    @Transactional
    public EscalationComment addInternalNote(long escalationId, String body) {
        Escalation escalation = get(escalationId);
        var verdict = authz.require(ESCALATION_INTERNAL_NOTE, escalation.getOrg().getId());
        if (body == null || body.isBlank() || body.length() > 4000) {
            throw new ValidationException("note text is required (max 4000 characters)");
        }
        EscalationComment comment = new EscalationComment(escalation, SecurityUtils.requireUser().userId(),
                SecurityUtils.requireUser().role().name(), CommentVisibility.INTERNAL, body.trim(), Instant.now());
        commentRepo.save(comment);
        audit.record("escalation.internal_note", "escalation_comment", comment.getId(),
                escalation.getOrg().getId(), null, null, verdict.authorizationBasis(), false);
        return comment;
    }

    /** Customer-visible update posted by staff, or a customer reply. */
    @Transactional
    public EscalationComment addCustomerVisibleUpdate(long escalationId, String body) {
        Escalation escalation = get(escalationId);
        var verdict = authz.require(ESCALATION_UPDATE, escalation.getOrg().getId());
        ActingPrincipal principal = SecurityUtils.requireUser();
        if (body == null || body.isBlank() || body.length() > 4000) {
            throw new ValidationException("message text is required (max 4000 characters)");
        }
        EscalationComment comment = new EscalationComment(escalation, principal.userId(),
                principal.role().name(), CommentVisibility.CUSTOMER_VISIBLE, body.trim(), Instant.now());
        commentRepo.save(comment);
        audit.record("escalation.customer_update", "escalation_comment", comment.getId(),
                escalation.getOrg().getId(), null, null, verdict.authorizationBasis(), true);
        webhookService.enqueueForOrg(escalation.getOrg().getId(), "escalation.updated", Map.of(
                "escalationId", escalationId, "event", "comment"));
        if (principal.role() == Role.CUSTOMER_CONTACT) {
            // A customer reply pulls the escalation back to the team.
            if (escalation.getStatus() == EscalationStatus.WAITING_ON_CUSTOMER
                    || escalation.getStatus() == EscalationStatus.WAITING_ON_INTERNAL) {
                escalation.moveTo(EscalationStatus.OPEN, Instant.now());
            }
            if (escalation.getAssigneeId() != null) {
                notifications.notify(escalation.getAssigneeId(), "escalation", "Customer replied",
                        escalation.getSubject() + " (" + escalation.getOrg().getName() + ")",
                        "escalations/" + escalationId);
            } else {
                notifySupportTeam("Customer replied: " + escalation.getSubject(),
                        escalation.getOrg().getName(), "escalations/" + escalationId);
            }
        } else {
            notifyOrgOwner(escalation, "Support update on " + escalation.getSubject(), body.trim());
        }
        return comment;
    }

    /** Comments filtered by role: customers only ever receive customer-visible ones. */
    @Transactional(readOnly = true)
    public List<CommentView> comments(long escalationId) {
        Escalation escalation = get(escalationId);
        ActingPrincipal principal = SecurityUtils.requireUser();
        boolean staff = principal.role() != Role.CUSTOMER_CONTACT;
        return commentRepo.findByEscalationIdOrderByCreatedAtAsc(escalationId).stream()
                .filter(c -> staff || c.getVisibility() == CommentVisibility.CUSTOMER_VISIBLE)
                .map(c -> {
                    String authorName = c.getAuthorId() == null ? "System"
                            : userRepo.findById(c.getAuthorId()).map(UserAccount::getDisplayName)
                                    .orElse(c.getAuthorRole());
                    return new CommentView(c.getId(), c.getAuthorId(), authorName, c.getAuthorRole(),
                            c.getVisibility().name(), c.getBody(), c.getCreatedAt());
                })
                .toList();
    }

    /** Dashboard aggregates for the support queue. */
    @Transactional(readOnly = true)
    public Map<String, Long> slaStats() {
        List<Long> scope = authz.orgScope(ESCALATION_READ);
        List<Escalation> open = escalationRepo.findByStatusInOrderBySeverityAscCreatedAtAsc(activeStatuses());
        if (scope != null) {
            open = open.stream().filter(e -> scope.contains(e.getOrg().getId())).toList();
        }
        return Map.of(
                "open", (long) open.size(),
                "breached", open.stream().filter(Escalation::isSlaBreached).count(),
                "sev1", open.stream().filter(e -> e.getSeverity() == Severity.SEV1).count(),
                "unassigned", open.stream().filter(e -> e.getAssigneeId() == null).count());
    }

    /** Staff escalation report rows (internal comments excluded from exports). */
    @Transactional(readOnly = true)
    public List<Escalation> reportRows() {
        List<Long> scope = authz.orgScope(ESCALATION_READ);
        List<Escalation> all = escalationRepo.findAll();
        if (scope == null) {
            return all;
        }
        return all.stream().filter(e -> scope.contains(e.getOrg().getId())).toList();
    }

    private EscalationView toView(Escalation e, Role viewerRole) {
        boolean staff = viewerRole != Role.CUSTOMER_CONTACT;
        String assigneeName = staff && e.getAssigneeId() != null
                ? userRepo.findById(e.getAssigneeId()).map(UserAccount::getDisplayName).orElse("—")
                : null;
        return new EscalationView(e.getId(), e.getOrg().getId(), e.getOrg().getName(), e.getSubject(),
                e.getDescription(), e.getSeverity().name(), e.getStatus().name(),
                staff ? e.getAssigneeId() : null, assigneeName, e.getCreatedAt(), e.getUpdatedAt(),
                staff ? e.getSlaDueAt() : null, staff && e.isSlaBreached(), staff ? e.getResolvedAt() : null);
    }

    private static List<EscalationStatus> activeStatuses() {
        return List.of(EscalationStatus.OPEN, EscalationStatus.WAITING_ON_CUSTOMER, EscalationStatus.WAITING_ON_INTERNAL);
    }

    private void notifySupportTeam(String title, String message, String link) {
        List<Long> agents = userRepo.findByRoleOrderByDisplayNameAsc(Role.SUPPORT_AGENT).stream()
                .map(UserAccount::getId)
                .toList();
        notifications.notifyMany(agents, "escalation", title, message, link);
    }

    private void notifyOrgOwner(Escalation escalation, String title, String message) {
        Organization org = escalation.getOrg();
        if (org.getOwnerUserId() != null) {
            notifications.notify(org.getOwnerUserId(), "escalation", title, message,
                    "escalations/" + escalation.getId());
        }
    }

    private static void validate(String subject, String description) {
        if (subject == null || subject.isBlank() || subject.length() > 200) {
            throw new ValidationException("subject is required (max 200 characters)");
        }
        if (description == null || description.isBlank() || description.length() > 4000) {
            throw new ValidationException("description is required (max 4000 characters)");
        }
    }
}
