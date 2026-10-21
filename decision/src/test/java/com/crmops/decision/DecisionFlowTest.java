package com.crmops.decision;

import com.crmops.common.api.Dto.LoginResponse;
import com.crmops.common.api.Dto.ScopeTokenRequest;
import com.crmops.common.api.Dto.SyncRequest;
import com.crmops.common.core.Actions;
import com.crmops.common.core.Roles;
import com.crmops.decision.service.AuthorizeService;
import com.crmops.decision.service.LoginFailedException;
import com.crmops.decision.service.LoginService;
import com.crmops.decision.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision-tier flow over its own database (H2, PostgreSQL mode): sync →
 * login → authorize with org scope, plus API-key scoping. No channel tier
 * involved — the decision tier evaluates everything from its own tables.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:decisiontest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="})
class DecisionFlowTest {

    @Autowired
    private SyncService syncService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private AuthorizeService authorizeService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        syncService.replace(new SyncRequest(
                List.of(
                        new com.crmops.common.api.Dto.SyncUser(101L, "sarah.chan", "Sarah Chan",
                                "sarah@crmops.local", Roles.ACCOUNT_MANAGER, true, null,
                                passwordEncoder.encode("Manager!Crmops2026")),
                        new com.crmops.common.api.Dto.SyncUser(102L, "erin.foley", "Erin Foley",
                                "erin@nimbus.io", Roles.CUSTOMER_CONTACT, true, 1L,
                                passwordEncoder.encode("Customer!Crmops2026")),
                        new com.crmops.common.api.Dto.SyncUser(103L, "disabled.user", "Disabled",
                                "disabled@crmops.local", Roles.SUPPORT_AGENT, false, null,
                                passwordEncoder.encode("Disabled!User2026"))),
                List.of(
                        new com.crmops.common.api.Dto.SyncAssignment(101L, 1L, "OWNER"),
                        new com.crmops.common.api.Dto.SyncAssignment(101L, 2L, "OWNER")),
                List.of(new com.crmops.common.api.Dto.SyncApiKey("hash-of-key-1", 1L, "Nimbus key",
                        "ACTIVE", Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS)))));
    }

    @Test
    void loginIssuesTokenAndAuthorizeUsesDatabaseState() {
        LoginResponse login = loginService.login("sarah.chan", "Manager!Crmops2026", "127.0.0.1");
        assertTrue(login.token().length() > 20);
        assertFalse(login.token().contains("sarah"), "token is opaque");

        var allowed = authorizeService.authorize(101L, Actions.ORG_READ, 1L);
        assertTrue(allowed.allowed());
        assertTrue(allowed.authorizationBasis().contains("ACCOUNT_MANAGER"));

        var denied = authorizeService.authorize(101L, Actions.ORG_READ, 99L);
        assertFalse(denied.allowed(), "unassigned org must be denied");

        var privilege = authorizeService.authorize(101L, Actions.USER_ADMIN, 1L);
        assertFalse(privilege.allowed(), "account managers can never administer users");

        var scope = authorizeService.scope(101L, Actions.ORG_READ);
        assertFalse(scope.unrestricted());
        assertEquals(List.of(1L, 2L), scope.allowedOrgIds());
    }

    @Test
    void loginRejectsWrongPasswordAndDisabledAccounts() {
        assertThrows(LoginFailedException.class,
                () -> loginService.login("sarah.chan", "wrong-password", "127.0.0.1"));
        assertThrows(LoginFailedException.class,
                () -> loginService.login("disabled.user", "Disabled!User2026", "127.0.0.1"));
        assertThrows(LoginFailedException.class,
                () -> loginService.login("nobody", "whatever", "127.0.0.1"));
    }

    @Test
    void customerScopeIsLockedToTheirOrg() {
        var ok = authorizeService.authorize(102L, Actions.DOCUMENT_READ, 1L);
        assertTrue(ok.allowed());
        var denied = authorizeService.authorize(102L, Actions.DOCUMENT_READ, 2L);
        assertFalse(denied.allowed(), "customer cannot read another org's documents");
        var scope = authorizeService.scope(102L, Actions.ORG_READ);
        assertEquals(List.of(1L), scope.allowedOrgIds());
    }

    @Test
    void apiKeyAuthorizationEnforcesSingleOrgScope() {
        var ok = authorizeService.authorizeApiKey("hash-of-key-1", Actions.ESCALATION_READ, 1L);
        assertTrue(ok.allowed());
        var wrongOrg = authorizeService.authorizeApiKey("hash-of-key-1", Actions.ESCALATION_READ, 2L);
        assertFalse(wrongOrg.allowed(), "api key cannot reach another org");
        var privilege = authorizeService.authorizeApiKey("hash-of-key-1", Actions.USER_ADMIN, 1L);
        assertFalse(privilege.allowed(), "api keys never carry admin actions");
        var unknown = authorizeService.authorizeApiKey("hash-does-not-exist", Actions.ESCALATION_READ, 1L);
        assertFalse(unknown.allowed());
    }

    @Test
    void scopeTokenMintingValidatesEveryOrgAgainstTheGrantedScope() {
        var request = new ScopeTokenRequest(List.of(1L), "invoices.read");
        var token = authorizeService.mintScopeToken(101L, request);
        assertTrue(token.token().length() > 20);
        var outOfScope = new ScopeTokenRequest(List.of(99L), "invoices.read");
        assertThrows(com.crmops.decision.service.AuthzDeniedException.class,
                () -> authorizeService.mintScopeToken(101L, outOfScope));
        var customerMint = new ScopeTokenRequest(List.of(1L), "invoices.read");
        assertThrows(com.crmops.decision.service.AuthzDeniedException.class,
                () -> authorizeService.mintScopeToken(102L, customerMint),
                "customers cannot mint billing scope tokens");
    }
}
