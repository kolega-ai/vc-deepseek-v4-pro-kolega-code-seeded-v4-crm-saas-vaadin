package com.crmops.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record BatchProperties(String batchAdminToken, String channelServiceToken, String decisionUrl,
                              int webhookTimeoutMs, int webhookMaxAttempts, int renewalWindowDays) {
}
