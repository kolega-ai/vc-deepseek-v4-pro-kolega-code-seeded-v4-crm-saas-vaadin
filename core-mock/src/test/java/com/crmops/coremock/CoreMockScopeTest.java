package com.crmops.coremock;

import com.crmops.common.auth.SignedToken;
import com.crmops.coremock.config.CoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vendor-core scope enforcement: a valid decision-minted scope token for org 1
 * cannot read org 2's invoices, and missing/garbage credentials fail with the
 * stable core-100x error codes.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:coremock;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.core-service-key=test-core-service-key",
        "app.scope-token-secret=test-scope-token-secret-0123456789abcdef"})
@AutoConfigureMockMvc
class CoreMockScopeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CoreProperties properties;

    private String scopeToken(List<Long> orgIds) {
        return SignedToken.mint(Map.of(
                        SignedToken.CLAIM_SUB, "42",
                        SignedToken.CLAIM_TYPE, SignedToken.TYPE_SCOPE,
                        SignedToken.CLAIM_SCOPE, "invoices.read",
                        SignedToken.CLAIM_ORG_IDS, orgIds),
                Duration.ofMinutes(5), "decision", properties.scopeTokenSecret());
    }

    @Test
    void orgInScopeReturnsInvoices() throws Exception {
        mockMvc.perform(get("/api/orgs/1/invoices")
                        .header("X-Core-Service-Key", "test-core-service-key")
                        .header("Authorization", "Bearer " + scopeToken(List.of(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(1))
                .andExpect(jsonPath("$.invoices.length()").value(3));
    }

    @Test
    void orgOutsideScopeIsForbiddenWithStableCode() throws Exception {
        mockMvc.perform(get("/api/orgs/2/invoices")
                        .header("X-Core-Service-Key", "test-core-service-key")
                        .header("Authorization", "Bearer " + scopeToken(List.of(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("core-1003"));
    }

    @Test
    void missingServiceKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/orgs/1/invoices")
                        .header("Authorization", "Bearer " + scopeToken(List.of(1L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("core-1001"));
    }

    @Test
    void invalidScopeTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/orgs/1/invoices")
                        .header("X-Core-Service-Key", "test-core-service-key")
                        .header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("core-1002"));
    }

    @Test
    void webhookSinkAcceptsAndRecordsDeliveries() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/mock/webhook-sink/demo-tenant")
                        .header("X-Crmops-Event", "escalation.created")
                        .contentType("application/json")
                        .content("{\"event\":\"escalation.created\",\"orgId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        mockMvc.perform(get("/mock/webhook-sink/demo-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenant").value("demo-tenant"));
    }
}
