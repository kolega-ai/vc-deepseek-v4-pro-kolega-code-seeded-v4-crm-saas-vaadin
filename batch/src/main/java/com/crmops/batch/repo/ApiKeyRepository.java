package com.crmops.batch.repo;

import com.crmops.batch.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByStatusAndExpiresAtBefore(String status, Instant now);
}
