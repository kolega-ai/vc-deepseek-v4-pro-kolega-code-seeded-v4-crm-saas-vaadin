package com.crmops.channel.repo;

import com.crmops.channel.domain.ApiKey;
import com.crmops.channel.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    @EntityGraph(attributePaths = "org")
    List<ApiKey> findByOrgIdOrderByCreatedAtDesc(Long orgId);

    List<ApiKey> findByStatusAndExpiresAtBefore(ApiKeyStatus status, Instant now);

    long countByOrgIdAndStatus(Long orgId, ApiKeyStatus status);
}
