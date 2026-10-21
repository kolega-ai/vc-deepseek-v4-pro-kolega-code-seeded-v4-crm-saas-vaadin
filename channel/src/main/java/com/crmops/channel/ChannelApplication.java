package com.crmops.channel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The channel tier: the Vaadin user interface and the CRM domain system of
 * record. It authenticates users (through the decision tier) but does NOT
 * authorize anything itself — every protected operation asks the decision tier
 * for a verdict and enforces it before touching data.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ChannelApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChannelApplication.class, args);
    }
}
