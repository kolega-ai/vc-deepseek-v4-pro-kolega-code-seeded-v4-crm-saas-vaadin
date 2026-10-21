package com.crmops.batch.repo;

import com.crmops.batch.domain.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    List<Escalation> findByStatusInAndSlaBreachedFalseAndSlaDueAtBefore(List<String> statuses, Instant now);
}
