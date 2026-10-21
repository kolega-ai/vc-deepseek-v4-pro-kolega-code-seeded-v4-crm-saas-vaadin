package com.crmops.coremock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record CoreProperties(String coreServiceKey, String scopeTokenSecret, int rateLimitPerMinute) {
}
