package com.crmops.channel.repo;

import com.crmops.channel.domain.OrgAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgAssignmentRepository extends JpaRepository<OrgAssignment, Long> {

    List<OrgAssignment> findByUserId(Long userId);

    List<OrgAssignment> findByOrgId(Long orgId);

    Optional<OrgAssignment> findByUserIdAndOrgId(Long userId, Long orgId);
}
