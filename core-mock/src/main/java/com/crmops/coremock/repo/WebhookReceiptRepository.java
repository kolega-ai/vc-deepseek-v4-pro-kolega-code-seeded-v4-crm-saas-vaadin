package com.crmops.coremock.repo;

import com.crmops.coremock.domain.WebhookReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookReceiptRepository extends JpaRepository<WebhookReceipt, Long> {

    List<WebhookReceipt> findTop20ByTenantOrderByReceivedAtDesc(String tenant);
}
