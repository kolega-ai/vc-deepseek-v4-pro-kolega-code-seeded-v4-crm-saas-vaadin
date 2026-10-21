package com.crmops.channel.service;

import com.crmops.channel.domain.AuditEvent;
import com.crmops.channel.repo.AuditEventRepository;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Activity timelines derived from the append-only audit trail. Customer
 * contacts only ever receive events flagged customer-visible; staff receive
 * the full org timeline including internal actions.
 */
@Service
public class ActivityService {

    private final AuditEventRepository auditRepo;

    public ActivityService(AuditEventRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> orgTimeline(long orgId) {
        Role role = SecurityUtils.requireUser().role();
        if (role == Role.CUSTOMER_CONTACT) {
            return auditRepo.findTop30ByOrgIdAndCustomerVisibleTrueOrderByOccurredAtDesc(orgId);
        }
        return auditRepo.findTop30ByOrgIdOrderByOccurredAtDesc(orgId);
    }
}
