package com.crmops.channel.repo;

import com.crmops.channel.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByActiveTrueOrderBySortOrderAsc();

    Optional<Plan> findByCode(String code);
}
