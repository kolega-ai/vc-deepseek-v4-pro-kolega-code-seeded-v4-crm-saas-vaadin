package com.crmops.channel.service;

import com.crmops.channel.domain.AssignmentRelation;
import com.crmops.channel.domain.OrgAssignment;
import com.crmops.channel.domain.OrgStatus;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Plan;
import com.crmops.channel.domain.Role;
import com.crmops.channel.repo.OrgAssignmentRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.repo.PlanRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.ActingPrincipal;
import com.crmops.channel.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.ORG_CREATE;
import static com.crmops.common.core.Actions.ORG_PLAN_CHANGE;
import static com.crmops.common.core.Actions.ORG_READ;
import static com.crmops.common.core.Actions.ORG_STATUS_CHANGE;
import static com.crmops.common.core.Actions.ORG_UPDATE;
import static com.crmops.common.core.Actions.USER_ADMIN;

/**
 * Organization lifecycle. Every operation is authorized against the decision
 * tier with the organization id; internal notes are gated behind ORG_UPDATE so
 * customer contacts and support agents can never read them.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository orgRepo;
    private final PlanRepository planRepo;
    private final OrgAssignmentRepository assignmentRepo;
    private final OrganizationQueryService queryService;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final DecisionSyncService sync;

    public OrganizationService(OrganizationRepository orgRepo, PlanRepository planRepo,
                               OrgAssignmentRepository assignmentRepo, OrganizationQueryService queryService,
                               CrmAuthorization authz, AuditService audit, DecisionSyncService sync) {
        this.orgRepo = orgRepo;
        this.planRepo = planRepo;
        this.assignmentRepo = assignmentRepo;
        this.queryService = queryService;
        this.authz = authz;
        this.audit = audit;
        this.sync = sync;
    }

    @Transactional(readOnly = true)
    public Organization get(long orgId) {
        authz.require(ORG_READ, orgId);
        return orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
    }

    @Transactional(readOnly = true)
    public List<Organization> search(OrganizationQueryService.SearchCriteria criteria) {
        // List reads are scoped by the decision tier: the org id set returned
        // here is the ONLY set the query may touch.
        List<Long> scope = authz.orgScope(ORG_READ);
        OrganizationQueryService.SearchCriteria scoped = scope == null
                ? criteria
                : new OrganizationQueryService.SearchCriteria(scope, criteria.search(), criteria.planCode(),
                        criteria.status(), criteria.healthBucket(), criteria.sort(), criteria.direction());
        return queryService.search(scoped);
    }

    @Transactional
    public Organization create(String name, Long planId, String industry, String country, Integer employeeCount,
                               int seats, String website, LocalDate renewalDate, String internalNotes) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        authz.require(ORG_CREATE);
        Plan plan = planRepo.findById(planId).orElseThrow(() -> new ValidationException("unknown plan"));
        if (name == null || name.isBlank() || name.length() > 160) {
            throw new ValidationException("organization name is required (max 160 characters)");
        }
        if (seats < 1 || seats > 100_000) {
            throw new ValidationException("seats must be between 1 and 100000");
        }
        Instant now = Instant.now();
        // Account managers automatically become the owner of orgs they create;
        // the OWNER assignment is what grants them access through decision.
        Long ownerUserId = principal.role() == Role.ACCOUNT_MANAGER ? principal.userId() : null;
        Organization org = new Organization(name.trim(), plan, OrgStatus.TRIAL, ownerUserId, renewalDate,
                60, null, industry, country, employeeCount, seats, website, internalNotes, now);
        orgRepo.save(org);
        if (ownerUserId != null) {
            assignmentRepo.save(new OrgAssignment(ownerUserId, org.getId(), AssignmentRelation.OWNER));
        }
        sync.fullSync();
        audit.record("org.create", "organization", org.getId(), org.getId(), null,
                Map.of("name", org.getName(), "plan", plan.getCode()),
                authz.require(ORG_CREATE).authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization updateProfile(long orgId, String name, String industry, String country,
                                      Integer employeeCount, int seats, String website) {
        var verdict = authz.require(ORG_UPDATE, orgId);
        Organization org = get(orgId);
        if (name == null || name.isBlank() || name.length() > 160) {
            throw new ValidationException("organization name is required (max 160 characters)");
        }
        if (seats < 1 || seats > 100_000) {
            throw new ValidationException("seats must be between 1 and 100000");
        }
        Map<String, Object> before = Map.of("name", org.getName(), "seats", org.getSeats());
        org.updateProfile(name.trim(), industry, country, employeeCount, seats, website, Instant.now());
        audit.record("org.profile_update", "organization", orgId, orgId, before,
                Map.of("name", org.getName(), "seats", org.getSeats()), verdict.authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization changePlan(long orgId, long planId) {
        var verdict = authz.require(ORG_PLAN_CHANGE, orgId);
        Organization org = get(orgId);
        Plan plan = planRepo.findById(planId).orElseThrow(() -> new ValidationException("unknown plan"));
        Plan before = org.getPlan();
        org.changePlan(plan, Instant.now());
        audit.record("org.plan_change", "organization", orgId, orgId,
                Map.of("plan", before.getCode()), Map.of("plan", plan.getCode()),
                verdict.authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization changeStatus(long orgId, OrgStatus status) {
        var verdict = authz.require(ORG_STATUS_CHANGE, orgId);
        Organization org = get(orgId);
        OrgStatus before = org.getStatus();
        org.changeStatus(status, Instant.now());
        audit.record("org.status_change", "organization", orgId, orgId,
                Map.of("status", before.name()), Map.of("status", status.name()),
                verdict.authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization updateRenewal(long orgId, LocalDate renewalDate) {
        var verdict = authz.require(ORG_UPDATE, orgId);
        Organization org = get(orgId);
        LocalDate before = org.getRenewalDate();
        org.setRenewal(renewalDate, Instant.now());
        audit.record("org.renewal_update", "organization", orgId, orgId,
                Map.of("renewalDate", before), Map.of("renewalDate", renewalDate),
                verdict.authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization updateHealth(long orgId, int score, String rationale) {
        var verdict = authz.require(ORG_UPDATE, orgId);
        if (score < 0 || score > 100) {
            throw new ValidationException("health score must be between 0 and 100");
        }
        Organization org = get(orgId);
        Map<String, Object> before = Map.of("healthScore", org.getHealthScore(),
                "healthRationale", org.getHealthRationale());
        org.updateHealth(score, rationale, Instant.now());
        audit.record("org.health_update", "organization", orgId, orgId, before,
                Map.of("healthScore", score, "healthRationale", rationale),
                verdict.authorizationBasis(), false);
        return org;
    }

    /** Internal account notes: ORG_UPDATE-gated, never visible to customers. */
    @Transactional(readOnly = true)
    public String internalNotes(long orgId) {
        authz.require(ORG_UPDATE, orgId);
        return get(orgId).getInternalNotes();
    }

    @Transactional
    public Organization updateInternalNotes(long orgId, String notes) {
        var verdict = authz.require(ORG_UPDATE, orgId);
        if (notes != null && notes.length() > 4000) {
            throw new ValidationException("notes too long (max 4000 characters)");
        }
        Organization org = get(orgId);
        org.setInternalNotes(notes, Instant.now());
        audit.record("org.internal_notes_update", "organization", orgId, orgId, null, null,
                verdict.authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization setBillingContact(long orgId, Long contactId) {
        var verdict = authz.require(ORG_UPDATE, orgId);
        Organization org = get(orgId);
        Long before = org.getBillingContactId();
        org.setBillingContact(contactId, Instant.now());
        audit.record("org.billing_contact", "organization", orgId, orgId,
                Map.of("billingContactId", before), Map.of("billingContactId", contactId),
                verdict.authorizationBasis(), false);
        return org;
    }

    @Transactional
    public Organization setOwner(long orgId, Long ownerUserId) {
        var verdict = authz.require(USER_ADMIN);
        Organization org = get(orgId);
        if (ownerUserId != null) {
            // Move the OWNER relation so decision grants the new owner access.
            assignmentRepo.findByOrgId(orgId).stream()
                    .filter(a -> a.getRelation() == AssignmentRelation.OWNER)
                    .forEach(assignmentRepo::delete);
            assignmentRepo.findByUserIdAndOrgId(ownerUserId, orgId)
                    .ifPresentOrElse(
                            existing -> existing.setRelation(AssignmentRelation.OWNER),
                            () -> assignmentRepo.save(new OrgAssignment(ownerUserId, orgId, AssignmentRelation.OWNER)));
        }
        Long before = org.getOwnerUserId();
        org.setOwner(ownerUserId, Instant.now());
        sync.fullSync();
        audit.record("org.owner_change", "organization", orgId, orgId,
                Map.of("ownerUserId", before), Map.of("ownerUserId", ownerUserId),
                verdict.authorizationBasis(), false);
        return org;
    }

    /**
     * Hands an account over to a new owner when a manager moves teams. The
     * OWNER assignment and the account's owner move together so decision grants
     * the new owner access immediately.
     */
    @Transactional
    public Organization handover(long orgId, Long newOwnerUserId) {
        authz.require(USER_ADMIN);
        Organization org = get(orgId);
        if (newOwnerUserId != null) {
            // Move the OWNER relation so decision grants the new owner access.
            assignmentRepo.findByOrgId(orgId).stream()
                    .filter(a -> a.getRelation() == AssignmentRelation.OWNER)
                    .forEach(assignmentRepo::delete);
            assignmentRepo.findByUserIdAndOrgId(newOwnerUserId, orgId)
                    .ifPresentOrElse(
                            existing -> existing.setRelation(AssignmentRelation.OWNER),
                            () -> assignmentRepo.save(new OrgAssignment(newOwnerUserId, orgId,
                                    AssignmentRelation.OWNER)));
        }
        org.setOwner(newOwnerUserId, Instant.now());
        sync.fullSync();
        orgRepo.save(org);
        return org;
    }

    @Transactional(readOnly = true)
    public List<Organization> renewalsDue(LocalDate from, LocalDate to) {
        List<Long> scope = authz.orgScope(ORG_READ);
        List<Organization> result = orgRepo.findRenewalsBetween(from, to);
        if (scope == null) {
            return result;
        }
        return result.stream().filter(o -> scope.contains(o.getId())).toList();
    }

    /** Organization counts per plan code (plan catalog screen). */
    @Transactional(readOnly = true)
    public Map<String, Long> orgCountsByPlan() {
        authz.require(com.crmops.common.core.Actions.PLAN_READ);
        return orgRepo.findAll().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        o -> o.getPlan().getCode(), java.util.stream.Collectors.counting()));
    }

    /** Fixed-precision monthly recurring revenue; computed from the reference table. */
    public static BigDecimal mrr(Organization org) {
        return org.getPlan().monthlyRevenueFor(org.getSeats());
    }
}
