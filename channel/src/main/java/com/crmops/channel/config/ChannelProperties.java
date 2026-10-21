package com.crmops.channel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ChannelProperties(String decisionUrl, String coreUrl, String channelServiceToken,
                                String coreServiceKey, String fileStorageDir, long maxUploadBytes,
                                long authzCacheSeconds, int webhookTimeoutMs, String baseUrl,
                                boolean seedEnabled, String webhookSinkBase, String directoryUrl,
                                String shareSigningKey) {

    /** Where seeded demo webhooks point (the core-mock receiver). */
    public String webhookSinkBase() {
        return webhookSinkBase == null || webhookSinkBase.isBlank()
                ? "http://localhost:8082" : webhookSinkBase;
    }
}
