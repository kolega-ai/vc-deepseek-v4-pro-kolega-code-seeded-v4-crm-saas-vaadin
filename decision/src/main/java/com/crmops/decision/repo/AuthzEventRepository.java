package com.crmops.decision.repo;

import com.crmops.decision.domain.AuthzEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthzEventRepository extends JpaRepository<AuthzEvent, Long> {
}
