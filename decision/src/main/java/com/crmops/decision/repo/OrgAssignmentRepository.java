package com.crmops.decision.repo;

import com.crmops.decision.domain.OrgAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrgAssignmentRepository extends JpaRepository<OrgAssignment, Long> {

    List<OrgAssignment> findByUserId(Long userId);

    List<OrgAssignment> findByOrgId(Long orgId);
}
