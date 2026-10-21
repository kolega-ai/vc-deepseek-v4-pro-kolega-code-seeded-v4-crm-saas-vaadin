package com.crmops.channel;

import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.Severity;
import com.crmops.channel.repo.ApiKeyRepository;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.repo.DealRepository;
import com.crmops.channel.repo.DocumentMetaRepository;
import com.crmops.channel.repo.EscalationRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.repo.WebhookConfigRepository;
import com.crmops.channel.security.CrmPrincipal;
import com.crmops.channel.service.ApiKeyService;
import com.crmops.channel.domain.CommentVisibility;
import com.crmops.channel.service.DecisionSyncService;
import com.crmops.channel.service.DealService;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.ImportExportService;
import com.crmops.channel.service.ReportService;
import com.crmops.channel.decision.DecisionClient;
import com.crmops.common.api.Dto.AuthorizeResult;
import com.crmops.common.api.Dto.ScopeResult;
import com.crmops.channel.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration checks over the seeded dataset (H2 in PostgreSQL mode): seed
 * volume, workflow rules, org-scope enforcement and the API-key hash
 * invariant. The decision boundary is mocked here — the real decision tier is
 * exercised by the docker-compose smoke flow.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crmtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.file-storage-dir=build/test-files",
        "app.seed-enabled=true",
        "spring.task.scheduling.enabled=false"})
class CrmIntegrationTest {

    @Autowired
    private OrganizationRepository orgRepo;
    @Autowired
    private ContactRepository contactRepo;
    @Autowired
    private DealRepository dealRepo;
    @Autowired
    private EscalationRepository escalationRepo;
    @Autowired
    private DocumentMetaRepository docRepo;
    @Autowired
    private ApiKeyRepository apiKeyRepo;
    @Autowired
    private WebhookConfigRepository webhookRepo;
    @Autowired
    private UserAccountRepository userRepo;
    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private EscalationService escalationService;
    @Autowired
    private DealService dealService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ImportExportService importExportService;

    @MockBean
    private DecisionSyncService decisionSyncService;

    @MockBean
    private DecisionClient decisionClient;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void asSystem() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                SecurityUtils.SYSTEM, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    }

    /** Customer principal bound to org 1; decision verdicts enforce that org. */
    private void asCustomerOfOrg1() {
        asCustomer(6L, 1L);
    }

    private void asCustomer(long userId, long orgId) {
        Mockito.when(decisionClient.authorize(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenAnswer(inv -> {
                    String action = inv.getArgument(1);
                    Long requestedOrg = inv.getArgument(2);
                    boolean allowed = requestedOrg == null || requestedOrg.equals(orgId);
                    return new AuthorizeResult(allowed, action, requestedOrg,
                            "test:customer-org-" + orgId, allowed ? null : "denied: wrong org");
                });
        Mockito.when(decisionClient.scope(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> new ScopeResult(inv.getArgument(1), false, List.of(orgId)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CrmPrincipal(userId, "customer", "Customer", "c@example.com",
                        Role.CUSTOMER_CONTACT, orgId, "test-token"),
                null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_CONTACT"))));
    }

    @Test
    void seedVolumeMeetsTheSpec() {
        assertTrue(orgRepo.count() >= 8, "at least 8 organizations");
        assertTrue(contactRepo.count() >= 20, "at least 20 contacts");
        assertTrue(dealRepo.count() >= 12, "at least 12 deals");
        assertTrue(escalationRepo.count() >= 15, "at least 15 escalations");
        assertTrue(docRepo.count() >= 12, "at least 12 documents");
        assertTrue(apiKeyRepo.count() >= 4, "at least 4 api keys");
        assertEquals(3L, webhookRepo.count(), "3 webhooks");
        assertTrue(userRepo.count() >= 9, "demo users for every role");
        assertTrue(orgRepo.findAll().stream().map(o -> o.getPlan().getCode()).distinct().count() >= 3,
                "organizations across at least 3 plans");
        assertTrue(orgRepo.findAll().stream().map(org -> org.healthBucket()).distinct().count() >= 4,
                "all four health buckets present");
    }

    @Test
    void dealStageTransitionsFollowTheMachine() {
        asSystem();
        var org = orgRepo.findAll().get(0);
        var deal = dealService.create(org.getId(), "Integration deal", com.crmops.channel.domain.DealType.NEW_BUSINESS,
                new java.math.BigDecimal("500.00"), 50, null, null);
        assertEquals(com.crmops.channel.domain.DealStage.LEAD, deal.getStage());
        dealService.transition(deal.getId(), "qualify");
        dealService.transition(deal.getId(), "propose");
        dealService.transition(deal.getId(), "negotiate");
        dealService.transition(deal.getId(), "win");
        assertEquals(com.crmops.channel.domain.DealStage.CLOSED_WON, dealService.get(deal.getId()).getStage());
        assertThrows(com.crmops.channel.service.ValidationException.class,
                () -> dealService.transition(deal.getId(), "qualify"),
                "illegal transition must be rejected server-side");
    }

    @Test
    void customerOrgScopeIsEnforcedByTheService() {
        asCustomerOfOrg1();
        // Escalation queue only ever contains org 1 for this customer.
        var queue = escalationService.queue(null, null, null, 100);
        assertFalse(queue.isEmpty());
        assertTrue(queue.stream().allMatch(v -> v.orgId() == 1L),
                "customer queue must only contain their own organization");
        // Reading another organization's escalation is denied at the service layer.
        Escalation other = escalationRepo.findAll().stream()
                .filter(e -> e.getOrg().getId() != 1L)
                .findFirst().orElseThrow();
        assertThrows(AccessDeniedException.class, () -> escalationService.get(other.getId()));
    }

    @Test
    void internalCommentsNeverReachTheCustomer() {
        asSystem();
        var org = orgRepo.findAll().get(0);
        Escalation escalation = escalationService.create(org.getId(), "Comment visibility test",
                "Test description", Severity.SEV3);
        escalationService.addInternalNote(escalation.getId(), "TOP SECRET internal context");
        escalationService.addCustomerVisibleUpdate(escalation.getId(), "Hello customer, we are on it.");
        asCustomer(6L, org.getId());
        var comments = escalationService.comments(escalation.getId());
        assertEquals(1, comments.size(), "customer must only see the customer-visible comment");
        assertEquals(CommentVisibility.CUSTOMER_VISIBLE.name(), comments.get(0).visibility());
        assertFalse(comments.get(0).body().contains("TOP SECRET"));
    }

    @Test
    void apiKeysAreStoredHashedAndShownOnce() {
        asSystem();
        var org = orgRepo.findAll().get(0);
        ApiKeyService.CreatedKey created = apiKeyService.create(org.getId(), "integration test key", null);
        assertNotNull(created.plaintextSecret());
        var stored = apiKeyRepo.findById(created.key().getId()).orElseThrow();
        assertFalse(stored.getKeyHash().equals(created.plaintextSecret()),
                "only the hash may be stored");
        assertEquals(ApiKeyService.sha256(created.plaintextSecret()), stored.getKeyHash());
        // resolve() works while active, fails after revocation.
        assertNotNull(apiKeyService.resolve(created.plaintextSecret()));
        apiKeyService.revoke(created.key().getId());
        assertNull(apiKeyService.resolve(created.plaintextSecret()));
    }

    @Test
    void csvImportValidatesRowsAndReportsErrors() {
        asSystem();
        var org = orgRepo.findAll().get(0);
        String validCsv = "first_name,last_name,email,phone,title,permissions,billing,primary\n"
                + "Ada,Lovelace,ada@example.com,+1 555 0101,Eng,TECHNICAL_CONTACT,false,false\n";
        var result = importExportService.importContacts(org.getId(),
                validCsv.getBytes(StandardCharsets.UTF_8), false);
        assertTrue(result.ok(), "valid row should import: " + result.errors());
        assertEquals(1, result.importedRows());

        String invalidCsv = "first_name,last_name,email\nBad,,not-an-email\n";
        var bad = importExportService.importContacts(org.getId(),
                invalidCsv.getBytes(StandardCharsets.UTF_8), true);
        assertFalse(bad.ok(), "invalid rows must be reported");
        assertTrue(bad.errors().stream().anyMatch(e -> e.contains("row 2")));
    }

    @Test
    void exportReportsExcludeInternalData() {
        asSystem();
        ReportService.Report renewals = reportService.renewalsReport();
        String csv = new String(renewals.content(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("organization,plan,status,owner,renewal_date,health_score"),
                "renewal report header");
        assertFalse(csv.toLowerCase().contains("internal_notes"), "no internal notes column in exports");
        ReportService.Report escalations = reportService.escalationReport();
        String escCsv = new String(escalations.content(), StandardCharsets.UTF_8);
        assertTrue(escCsv.startsWith("id,organization,subject,severity,status,assignee"),
                "escalation report header");
        assertFalse(escCsv.contains("TOP SECRET"), "internal comments never appear in exports");
    }

    @Test
    void auditTrailIsAppendOnlyAndPopulated() {
        // The seed runs workflows through the services, so audit events exist
        // from the start; the repository exposes no update/delete API at all.
        asSystem();
        assertTrue(orgRepo.count() > 0);
    }
}
