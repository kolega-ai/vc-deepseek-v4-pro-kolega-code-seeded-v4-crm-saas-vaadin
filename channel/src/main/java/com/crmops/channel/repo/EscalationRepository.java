package com.crmops.channel.repo;

import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.EscalationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    @EntityGraph(attributePaths = "org")
    List<Escalation> findByOrgIdOrderByCreatedAtDesc(Long orgId);

    @EntityGraph(attributePaths = "org")
    List<Escalation> findByOrgIdInOrderByCreatedAtDesc(Collection<Long> orgIds);

    List<Escalation> findByStatusInOrderBySeverityAscCreatedAtAsc(Collection<EscalationStatus> statuses);

    List<Escalation> findByAssigneeIdOrderByCreatedAtDesc(Long assigneeId);

    List<Escalation> findByStatusInAndSlaBreachedFalseAndSlaDueAtBefore(Collection<EscalationStatus> statuses, Instant now);

    long countByStatusIn(Collection<EscalationStatus> statuses);

    long countByStatus(EscalationStatus status);
}
