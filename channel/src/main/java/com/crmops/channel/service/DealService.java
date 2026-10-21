package com.crmops.channel.service;

import com.crmops.channel.domain.Deal;
import com.crmops.channel.domain.DealStage;
import com.crmops.channel.domain.DealType;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.repo.DealRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.workflow.DealStageMachine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.DEAL_CREATE;
import static com.crmops.common.core.Actions.DEAL_READ;
import static com.crmops.common.core.Actions.DEAL_STAGE_CHANGE;
import static com.crmops.common.core.Actions.DEAL_UPDATE;

/**
 * Deal and renewal management. Stage changes run through the explicit
 * DealStageMachine edge list; money is fixed-precision BigDecimal throughout.
 */
@Service
public class DealService {

    private final DealRepository dealRepo;
    private final OrganizationRepository orgRepo;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final WebhookService webhookService;
    private final NotificationService notifications;

    public DealService(DealRepository dealRepo, OrganizationRepository orgRepo, CrmAuthorization authz,
                       AuditService audit, WebhookService webhookService, NotificationService notifications) {
        this.dealRepo = dealRepo;
        this.orgRepo = orgRepo;
        this.authz = authz;
        this.audit = audit;
        this.webhookService = webhookService;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<Deal> list(long orgId) {
        authz.require(DEAL_READ, orgId);
        return dealRepo.findByOrgIdOrderByUpdatedAtDesc(orgId);
    }

    @Transactional(readOnly = true)
    public Deal get(long dealId) {
        Deal deal = dealRepo.findById(dealId).orElseThrow(() -> new NotFoundException("deal", dealId));
        authz.require(DEAL_READ, deal.getOrg().getId());
        return deal;
    }

    @Transactional
    public Deal create(long orgId, String name, DealType type, BigDecimal amount, int probability,
                       LocalDate closeDate, String notes) {
        var verdict = authz.require(DEAL_CREATE, orgId);
        validate(name, amount, probability);
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        Deal deal = new Deal(org, name.trim(), type, DealStage.LEAD, amount, "USD", probability, closeDate, notes,
                SecurityUtils.requireUser().userId(), Instant.now());
        dealRepo.save(deal);
        audit.record("deal.create", "deal", deal.getId(), orgId, null,
                Map.of("name", deal.getName(), "amount", deal.getAmount().toPlainString(), "stage", deal.getStage().name()),
                verdict.authorizationBasis(), false);
        return deal;
    }

    @Transactional
    public Deal update(long dealId, String name, BigDecimal amount, int probability, LocalDate closeDate, String notes) {
        Deal deal = get(dealId);
        var verdict = authz.require(DEAL_UPDATE, deal.getOrg().getId());
        validate(name, amount, probability);
        Map<String, Object> before = Map.of("name", deal.getName(), "amount", deal.getAmount().toPlainString(),
                "probability", deal.getProbability());
        deal.update(name.trim(), amount, probability, closeDate, notes, Instant.now());
        audit.record("deal.update", "deal", dealId, deal.getOrg().getId(), before,
                Map.of("name", deal.getName(), "amount", deal.getAmount().toPlainString(),
                        "probability", deal.getProbability()),
                verdict.authorizationBasis(), false);
        return deal;
    }

    /** Advances the deal along the explicit transition edge list. */
    @Transactional
    public Deal transition(long dealId, String action) {
        Deal deal = get(dealId);
        var verdict = authz.require(DEAL_STAGE_CHANGE, deal.getOrg().getId());
        if (!DealStageMachine.isAllowed(deal.getStage(), action)) {
            throw new ValidationException("cannot '" + action + "' a deal in stage " + deal.getStage().label());
        }
        DealStage before = deal.getStage();
        DealStage target = DealStageMachine.target(deal.getStage(), action);
        deal.moveTo(target, Instant.now());
        if (target == DealStage.CLOSED_WON && deal.getDealType() == DealType.NEW_BUSINESS) {
            // winning a new-business deal converts the org out of trial
            Organization org = deal.getOrg();
            if (org.getStatus() == com.crmops.channel.domain.OrgStatus.TRIAL) {
                org.changeStatus(com.crmops.channel.domain.OrgStatus.ACTIVE, Instant.now());
            }
        }
        audit.record("deal.stage_change", "deal", dealId, deal.getOrg().getId(),
                Map.of("stage", before.name()), Map.of("stage", target.name()),
                verdict.authorizationBasis(), false);
        webhookService.enqueueForOrg(deal.getOrg().getId(), "deal.stage_changed", Map.of(
                "dealId", dealId, "orgId", deal.getOrg().getId(), "stage", target.name(),
                "amount", deal.getAmount().toPlainString()));
        if (deal.getOwnerUserId() != null) {
            notifications.notify(deal.getOwnerUserId(), "deal",
                    "Deal moved to " + target.label(),
                    deal.getName() + " (" + deal.getOrg().getName() + ") is now " + target.label() + ".",
                    "organizations/" + deal.getOrg().getId());
        }
        return deal;
    }

    /** Pipeline aggregates for a scope of organizations (dashboard). */
    @Transactional(readOnly = true)
    public PipelineSummary pipelineSummary() {
        List<Long> scope = authz.orgScope(DEAL_READ);
        List<Deal> deals = dealRepo.findByStageIn(List.of(DealStage.LEAD, DealStage.QUALIFIED,
                DealStage.PROPOSAL, DealStage.NEGOTIATION));
        if (scope != null) {
            deals = deals.stream().filter(d -> scope.contains(d.getOrg().getId())).toList();
        }
        BigDecimal expected = deals.stream()
                .map(Deal::expectedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = deals.size();
        Map<String, Long> byStage = deals.stream()
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getStage().name(),
                        java.util.stream.Collectors.counting()));
        return new PipelineSummary(count, expected.setScale(2, java.math.RoundingMode.HALF_UP), byStage);
    }

    public record PipelineSummary(long openDeals, BigDecimal expectedValue, Map<String, Long> byStage) {
    }

    private static void validate(String name, BigDecimal amount, int probability) {
        if (name == null || name.isBlank() || name.length() > 160) {
            throw new ValidationException("deal name is required (max 160 characters)");
        }
        if (amount == null || amount.signum() < 0 || amount.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new ValidationException("amount must be between 0 and 1,000,000,000");
        }
        if (probability < 0 || probability > 100) {
            throw new ValidationException("probability must be between 0 and 100");
        }
    }
}
