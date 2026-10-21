package com.crmops.channel.repo;

import com.crmops.channel.domain.Deal;
import com.crmops.channel.domain.DealStage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @EntityGraph(attributePaths = "org")
    List<Deal> findByOrgIdOrderByUpdatedAtDesc(Long orgId);

    List<Deal> findByOrgIdIn(Collection<Long> orgIds);

    List<Deal> findByStageIn(Collection<DealStage> stages);
}
