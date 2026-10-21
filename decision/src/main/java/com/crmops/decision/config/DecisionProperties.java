package com.crmops.decision.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record DecisionProperties(String channelServiceToken, String coreTokenSecret, String sessionTokenSecret,
                                 long sessionTokenTtlMinutes, long scopeTokenTtlMinutes) {
}
