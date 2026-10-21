package com.crmops.batch.repo;

import com.crmops.batch.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findTop20ByStatusAndAttemptLessThanOrderByCreatedAtAsc(String status, int attempt);
}
