package com.crmops.channel.repo;

import com.crmops.channel.domain.WebhookConfig;
import com.crmops.channel.domain.WebhookConfigStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    @EntityGraph(attributePaths = "org")
    List<WebhookConfig> findByOrgIdOrderByCreatedAtDesc(Long orgId);

    List<WebhookConfig> findByStatus(WebhookConfigStatus status);
}
