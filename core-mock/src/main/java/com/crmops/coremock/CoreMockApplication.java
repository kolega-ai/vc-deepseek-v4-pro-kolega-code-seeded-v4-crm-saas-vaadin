package com.crmops.coremock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Vendor-core stub. From the channel tier's point of view this is an external
 * system: it has its own network endpoint, its own credential and its own
 * stable error codes. It only serves data to callers presenting a
 * decision-minted scope token, and it enforces the token's organization claim
 * itself — a raw org id in the URL or query is never trusted.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CoreMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreMockApplication.class, args);
    }
}
