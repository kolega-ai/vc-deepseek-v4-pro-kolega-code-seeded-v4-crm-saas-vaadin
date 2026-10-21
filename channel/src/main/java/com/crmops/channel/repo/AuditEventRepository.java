package com.crmops.channel.repo;

import com.crmops.channel.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    List<AuditEvent> findTop30ByOrgIdOrderByOccurredAtDesc(Long orgId);

    List<AuditEvent> findTop30ByOrgIdAndCustomerVisibleTrueOrderByOccurredAtDesc(Long orgId);
}
