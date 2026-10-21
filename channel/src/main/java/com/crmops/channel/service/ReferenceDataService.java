package com.crmops.channel.service;

import com.crmops.channel.domain.Plan;
import com.crmops.channel.repo.PlanRepository;
import com.crmops.channel.security.CrmAuthorization;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.crmops.common.core.Actions.PLAN_READ;

/** Reference data (plan catalog). Prices live in the seeded plans table only. */
@Service
public class ReferenceDataService {

    private final PlanRepository planRepo;
    private final CrmAuthorization authz;

    public ReferenceDataService(PlanRepository planRepo, CrmAuthorization authz) {
        this.planRepo = planRepo;
        this.authz = authz;
    }

    public List<Plan> activePlans() {
        authz.require(PLAN_READ);
        return planRepo.findByActiveTrueOrderBySortOrderAsc();
    }
}
