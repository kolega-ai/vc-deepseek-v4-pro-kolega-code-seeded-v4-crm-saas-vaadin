package com.crmops.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The batch tier: in-process scheduled workflows over the channel database.
 * Jobs: email-outbox processing, webhook delivery retries, API-key expiry,
 * escalation SLA flagging and renewal reminders. Every state change a job
 * makes is recorded in the append-only audit trail with a SYSTEM basis.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }
}
