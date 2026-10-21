package com.crmops.decision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The decision tier: the AUTHORITATIVE source of who a user is, what role they
 * hold and which organizations they may touch. The channel tier forwards an
 * opaque signed session token with every authorization request; identity,
 * role, scope and limits are resolved from this service's own database on
 * every request. No user id, role, company, scope or limit supplied in a
 * header, path, query, form field or request body is ever trusted here.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DecisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecisionApplication.class, args);
    }
}
