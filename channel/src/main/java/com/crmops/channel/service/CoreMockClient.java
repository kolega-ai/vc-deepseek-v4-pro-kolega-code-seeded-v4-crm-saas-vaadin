package com.crmops.channel.service;

import com.crmops.common.api.Dto.Invoice;
import com.crmops.channel.config.ChannelProperties;
import com.crmops.channel.security.CrmAuthorization;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static com.crmops.common.core.Actions.BILLING_READ;

/**
 * Client for the vendor billing core (core-mock). Calls are made with the
 * channel's service credential plus a decision-minted scope token; the vendor
 * core enforces the token's organization claim itself, so a raw org id here is
 * only ever a hint.
 */
@Component
public class CoreMockClient {

    private final RestClient rest;
    private final String serviceKey;
    private final CrmAuthorization authz;

    public CoreMockClient(ChannelProperties properties, CrmAuthorization authz) {
        this.rest = RestClient.builder().baseUrl(properties.coreUrl()).build();
        this.serviceKey = properties.coreServiceKey();
        this.authz = authz;
    }

    public List<Invoice> invoices(long orgId) {
        // Billing data is gated by decision (billing.read + org scope) and the
        // scope token is minted by decision for exactly this org.
        authz.require(BILLING_READ, orgId);
        String scopeToken = authz.mintScopeToken(List.of(orgId), "invoices.read");
        try {
            var list = rest.get()
                    .uri("/api/orgs/{orgId}/invoices", orgId)
                    .header("X-Core-Service-Key", serviceKey)
                    .header("Authorization", "Bearer " + scopeToken)
                    .retrieve()
                    .body(com.crmops.common.api.Dto.InvoiceList.class);
            return list == null || list.invoices() == null ? List.of() : list.invoices();
        } catch (RestClientResponseException e) {
            throw new VendorCoreException("vendor billing core error (status " + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            throw new VendorCoreException("vendor billing core unreachable");
        }
    }

    /** Vendor-core failures surface as a user-facing message, never a stack trace. */
    public static class VendorCoreException extends RuntimeException {
        public VendorCoreException(String message) {
            super(message);
        }
    }
}
