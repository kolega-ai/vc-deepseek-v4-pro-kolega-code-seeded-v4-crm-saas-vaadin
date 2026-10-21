package com.crmops.channel.repo;

import com.crmops.channel.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findTop50ByConfigIdOrderByCreatedAtDesc(Long configId);

    List<WebhookDelivery> findTop50ByOrgIdOrderByCreatedAtDesc(Long orgId);

    List<WebhookDelivery> findTop20ByStatusOrderByCreatedAtAsc(com.crmops.channel.domain.DeliveryStatus status);
}
