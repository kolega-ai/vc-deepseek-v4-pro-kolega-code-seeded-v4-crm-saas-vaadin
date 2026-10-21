package com.crmops.decision.repo;

import com.crmops.decision.domain.ApiKeyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRecordRepository extends JpaRepository<ApiKeyRecord, String> {

    Optional<ApiKeyRecord> findByKeyHash(String keyHash);
}
