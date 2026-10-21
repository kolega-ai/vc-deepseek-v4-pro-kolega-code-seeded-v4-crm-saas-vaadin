package com.crmops.channel.service;

import com.crmops.channel.config.ChannelProperties;
import com.crmops.channel.domain.ApiKeyStatus;
import com.crmops.channel.domain.AssignmentRelation;
import com.crmops.channel.domain.CommentVisibility;
import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.Deal;
import com.crmops.channel.domain.DealStage;
import com.crmops.channel.domain.DealType;
import com.crmops.channel.domain.DocCategory;
import com.crmops.channel.domain.DocVisibility;
import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.EscalationComment;
import com.crmops.channel.domain.EscalationStatus;
import com.crmops.channel.domain.OrgAssignment;
import com.crmops.channel.domain.OrgStatus;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Plan;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.SeedMarker;
import com.crmops.channel.domain.Severity;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.domain.WebhookConfig;
import com.crmops.channel.domain.WebhookConfigStatus;
import com.crmops.channel.repo.ApiKeyRepository;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.repo.DealRepository;
import com.crmops.channel.repo.DocumentMetaRepository;
import com.crmops.channel.repo.EscalationCommentRepository;
import com.crmops.channel.repo.EscalationRepository;
import com.crmops.channel.repo.OrgAssignmentRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.repo.PlanRepository;
import com.crmops.channel.repo.SeedMarkerRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.repo.WebhookConfigRepository;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.workflow.ApiKeyStateMachine;
import com.crmops.channel.workflow.DealStageMachine;
import com.crmops.channel.workflow.EscalationStatusMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Idempotent demo-data seeder. Every seeded status is produced by the SAME
 * state-machine actions the UI uses (no unreachable states are seeded
 * directly), and seeded API keys are stored as SHA-256 hashes — the plaintext
 * values are printed to the log exactly once, matching the "show once"
 * behavior of the UI. Seeding runs as the SYSTEM actor and finishes with a
 * full decision-tier sync.
 */
@Component
public class SeedService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final String SEED_KEY = "demo-seed-v1";

    // Demo passwords (documented in the README).
    private static final String ADMIN_PW = "Admin!Crmops2026";
    private static final String MANAGER_PW = "Manager!Crmops2026";
    private static final String SUPPORT_PW = "Support!Crmops2026";
    private static final String CUSTOMER_PW = "Customer!Crmops2026";

    private final SeedMarkerRepository markerRepo;
    private final UserAccountRepository userRepo;
    private final PlanRepository planRepo;
    private final OrganizationRepository orgRepo;
    private final OrgAssignmentRepository assignmentRepo;
    private final ContactRepository contactRepo;
    private final DealRepository dealRepo;
    private final EscalationRepository escalationRepo;
    private final EscalationCommentRepository commentRepo;
    private final DocumentMetaRepository docRepo;
    private final ApiKeyRepository apiKeyRepo;
    private final WebhookConfigRepository webhookRepo;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorage;
    private final DecisionSyncService sync;
    private final ChannelProperties properties;
    private final AuditService audit;
    private final NotificationService notifications;

    public SeedService(SeedMarkerRepository markerRepo, UserAccountRepository userRepo, PlanRepository planRepo,
                       OrganizationRepository orgRepo, OrgAssignmentRepository assignmentRepo,
                       ContactRepository contactRepo, DealRepository dealRepo, EscalationRepository escalationRepo,
                       EscalationCommentRepository commentRepo, DocumentMetaRepository docRepo,
                       ApiKeyRepository apiKeyRepo, WebhookConfigRepository webhookRepo,
                       PasswordEncoder passwordEncoder, FileStorageService fileStorage, DecisionSyncService sync,
                       ChannelProperties properties, AuditService audit, NotificationService notifications) {
        this.markerRepo = markerRepo;
        this.userRepo = userRepo;
        this.planRepo = planRepo;
        this.orgRepo = orgRepo;
        this.assignmentRepo = assignmentRepo;
        this.contactRepo = contactRepo;
        this.dealRepo = dealRepo;
        this.escalationRepo = escalationRepo;
        this.commentRepo = commentRepo;
        this.docRepo = docRepo;
        this.apiKeyRepo = apiKeyRepo;
        this.webhookRepo = webhookRepo;
        this.passwordEncoder = passwordEncoder;
        this.fileStorage = fileStorage;
        this.sync = sync;
        this.properties = properties;
        this.audit = audit;
        this.notifications = notifications;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.seedEnabled()) {
            log.info("seeding disabled by configuration");
            return;
        }
        if (markerRepo.existsById(SEED_KEY)) {
            log.info("seed already present; skipping");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                SecurityUtils.SYSTEM, null, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        try {
            seed();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void seed() {
        log.info("seeding demo data…");
        Instant now = Instant.now();

        Plan starter = planRepo.findByCode("STARTER").orElseThrow();
        Plan pro = planRepo.findByCode("PRO").orElseThrow();
        Plan enterprise = planRepo.findByCode("ENTERPRISE").orElseThrow();

        // --- users ---
        UserAccount admin = user(1L, "admin", "Avery Stone", "admin@crmops.local", Role.PLATFORM_ADMIN, null,
                "Platform Administrator", ADMIN_PW);
        UserAccount sarah = user(2L, "sarah.chan", "Sarah Chan", "sarah.chan@crmops.local", Role.ACCOUNT_MANAGER, null,
                "Senior Account Manager", MANAGER_PW);
        UserAccount david = user(3L, "david.okafor", "David Okafor", "david.okafor@crmops.local", Role.ACCOUNT_MANAGER,
                null, "Account Manager", MANAGER_PW);
        UserAccount lee = user(4L, "lee.support", "Lee Martinez", "lee.support@crmops.local", Role.SUPPORT_AGENT, null,
                "Support Agent", SUPPORT_PW);
        UserAccount maya = user(5L, "maya.support", "Maya Osei", "maya.support@crmops.local", Role.SUPPORT_AGENT, null,
                "Senior Support Agent", SUPPORT_PW);
        UserAccount erin = user(6L, "erin.foley", "Erin Foley", "erin.foley@nimbuscloud.io", Role.CUSTOMER_CONTACT, 1L,
                "VP Operations", CUSTOMER_PW);
        UserAccount marcus = user(7L, "marcus.webb", "Marcus Webb", "marcus.webb@aperturelabs.com",
                Role.CUSTOMER_CONTACT, 2L, "IT Director", CUSTOMER_PW);
        UserAccount priya = user(8L, "priya.nair", "Priya Nair", "priya.nair@brightline.co", Role.CUSTOMER_CONTACT, 3L,
                "Head of CS", CUSTOMER_PW);
        UserAccount tomas = user(9L, "tomas.reyes", "Tomas Reyes", "tomas.reyes@vertexdata.io",
                Role.CUSTOMER_CONTACT, 4L, "CTO", CUSTOMER_PW);

        // --- organizations ---
        Organization nimbus = org(1L, "Nimbus Cloud", pro, OrgStatus.ACTIVE, sarah.getId(), LocalDate.now().plusDays(48),
                82, "Steady usage growth; strong CSAT; expansion conversations active.",
                "Cloud infrastructure", "United States", 340, 25, "https://nimbuscloud.io",
                "Decision maker changed in Q2 — budget approvals now go through the new CFO.", now);
        Organization aperture = org(2L, "Aperture Labs", enterprise, OrgStatus.ACTIVE, sarah.getId(),
                LocalDate.now().plusDays(120), 91, "Power user across three business units; reference customer.",
                "Developer tooling", "Canada", 1200, 90, "https://aperturelabs.com",
                "Security review passed 2026-06; annual contract includes custom SLA rider.", now);
        Organization brightline = org(3L, "Brightline", starter, OrgStatus.ACTIVE, david.getId(),
                LocalDate.now().plusDays(21), 64, "Renewal due soon; usage dipped after champion left.",
                "Fintech", "United Kingdom", 60, 8, "https://brightline.co",
                "Champion (C. Walsh) left in August. Renewal needs executive sponsor — escalate if no reply by day 14.",
                now);
        Organization vertex = org(4L, "Vertex Data", pro, OrgStatus.TRIAL, david.getId(),
                LocalDate.now().plusDays(150), 55, "Mid-trial; platform fit confirmed but budget unproven.",
                "Data analytics", "Germany", 210, 18, "https://vertexdata.io",
                "Trial ends " + LocalDate.now().plusDays(20) + "; POC goals 60% met.", now);
        Organization helios = org(5L, "Helios Energy", enterprise, OrgStatus.ACTIVE, sarah.getId(),
                LocalDate.now().plusDays(75), 74, "Solid account; slow invoice approval process.",
                "Energy", "Norway", 890, 70, "https://heliosenergy.no",
                "Procurement requires PO per invoice; remind billing contact before each cycle.", now);
        Organization orbital = org(6L, "Orbital Systems", pro, OrgStatus.SUSPENDED, david.getId(),
                LocalDate.now().plusDays(200), 38, "Suspended for non-payment; recovery plan in progress.",
                "Aerospace", "United States", 480, 30, "https://orbitalsystems.com",
                "Two overdue invoices; legal review ongoing. Do not upsell until status is ACTIVE again.", now);
        Organization quantic = org(7L, "Quantic Analytics", starter, OrgStatus.CHURNED, david.getId(),
                LocalDate.now().minusDays(40), 22, "Churned after acquisition; account closed by buyer.",
                "Market research", "Australia", 45, 5, "https://quanticanalytics.au",
                "Churn reason: acquired by Helios-adjacent holding co. No win-back path.", now);
        Organization fjordline = org(8L, "Fjordline Logistics", pro, OrgStatus.ACTIVE, sarah.getId(),
                LocalDate.now().plusDays(160), 88, "Fast-growing; talking about moving to Enterprise next year.",
                "Logistics", "Sweden", 260, 28, "https://fjordline.se",
                "Enterprise upgrade conversation scheduled for next quarter.", now);

        // staff assignments
        assign(sarah, List.of(nimbus, aperture, helios, fjordline));
        assign(david, List.of(brightline, vertex, orbital, quantic));
        assign(lee, List.of(nimbus, brightline, vertex));
        assign(maya, List.of(aperture, helios, orbital, fjordline));

        // --- contacts (20+) ---
        contact(nimbus, "Erin", "Foley", "erin.foley@nimbuscloud.io", "+1 415 555 0101", "VP Operations",
                "PORTAL_ADMIN,BILLING_CONTACT", true, true, erin.getId(), now);
        contact(nimbus, "Sam", "Iqbal", "sam.iqbal@nimbuscloud.io", "+1 415 555 0102", "Platform Engineer",
                "TECHNICAL_CONTACT", false, false, null, now);
        contact(nimbus, "Jordan", "Ash", "jordan.ash@nimbuscloud.io", "+1 415 555 0103", "Procurement",
                "BILLING_CONTACT", false, false, null, now);
        contact(aperture, "Marcus", "Webb", "marcus.webb@aperturelabs.com", "+1 604 555 0110", "IT Director",
                "PORTAL_ADMIN,BILLING_CONTACT", true, true, marcus.getId(), now);
        contact(aperture, "Lena", "Kovacs", "lena.kovacs@aperturelabs.com", "+1 604 555 0111", "Security Lead",
                "TECHNICAL_CONTACT", false, false, null, now);
        contact(aperture, "Owen", "Delgado", "owen.delgado@aperturelabs.com", "+1 604 555 0112", "VP Product",
                "PRIMARY_CONTACT", false, false, null, now);
        contact(brightline, "Priya", "Nair", "priya.nair@brightline.co", "+44 20 7946 0121", "Head of CS",
                "PORTAL_ADMIN", true, true, priya.getId(), now);
        contact(brightline, "Callum", "Walsh", "callum.walsh@brightline.co", "+44 20 7946 0122", "COO",
                "PRIMARY_CONTACT,BILLING_CONTACT", false, false, null, now);
        contact(vertex, "Tomas", "Reyes", "tomas.reyes@vertexdata.io", "+49 30 555 0140", "CTO",
                "PORTAL_ADMIN,TECHNICAL_CONTACT", true, true, tomas.getId(), now);
        contact(vertex, "Anke", "Braun", "anke.braun@vertexdata.io", "+49 30 555 0141", "Data Platform Lead",
                "TECHNICAL_CONTACT", false, false, null, now);
        contact(helios, "Bjorn", "Halvorsen", "bjorn.halvorsen@heliosenergy.no", "+47 22 555 0150", "IT Manager",
                "PORTAL_ADMIN,BILLING_CONTACT", true, true, null, now);
        contact(helios, "Ingrid", "Solberg", "ingrid.solberg@heliosenergy.no", "+47 22 555 0151", "Procurement",
                "BILLING_CONTACT", false, false, null, now);
        contact(orbital, "Ray", "Nguyen", "ray.nguyen@orbitalsystems.com", "+1 281 555 0160", "Engineering Director",
                "PORTAL_ADMIN", true, true, null, now);
        contact(orbital, "Casey", "Marsh", "casey.marsh@orbitalsystems.com", "+1 281 555 0161", "Finance",
                "BILLING_CONTACT", false, false, null, now);
        contact(quantic, "Mia", "Harper", "mia.harper@quanticanalytics.au", "+61 2 555 0170", "GM",
                "PRIMARY_CONTACT", true, true, null, now);
        contact(fjordline, "Sven", "Lindqvist", "sven.lindqvist@fjordline.se", "+46 8 555 0180", "COO",
                "PORTAL_ADMIN,BILLING_CONTACT", true, true, null, now);
        contact(fjordline, "Astrid", "Berg", "astrid.berg@fjordline.se", "+46 8 555 0181", "Operations Lead",
                "PRIMARY_CONTACT", false, false, null, now);
        contact(fjordline, "Nils", "Ek", "nils.ek@fjordline.se", "+46 8 555 0182", "Integrations Engineer",
                "TECHNICAL_CONTACT", false, false, null, now);
        contact(aperture, "Zoe", "Winters", "zoe.winters@aperturelabs.com", "+1 604 555 0113", "Finance Director",
                "BILLING_CONTACT", true, false, null, now);
        contact(nimbus, "Diego", "Cruz", "diego.cruz@nimbuscloud.io", "+1 415 555 0104", "Support Lead",
                "TECHNICAL_CONTACT", false, false, null, now);
        contact(vertex, "Hannah", "Vogt", "hannah.vogt@vertexdata.io", "+49 30 555 0142", "COO",
                "PRIMARY_CONTACT,BILLING_CONTACT", false, false, null, now);
        contact(helios, "Magnus", "Vik", "magnus.vik@heliosenergy.no", "+47 22 555 0152", "Energy Data Lead",
                "TECHNICAL_CONTACT", false, false, null, now);

        // --- deals: every stage produced via the state machine's edges ---
        Deal d1 = deal(nimbus, "Nimbus Enterprise expansion", DealType.NEW_BUSINESS, "9800.00", 40,
                LocalDate.now().plusDays(45), "Expansion to 100 seats across two regions.", sarah.getId(), now);
        stage(d1, DealStageMachine.ACTION_QUALIFY, now.minus(40, ChronoUnit.DAYS));
        stage(d1, DealStageMachine.ACTION_PROPOSE, now.minus(30, ChronoUnit.DAYS));
        stage(d1, DealStageMachine.ACTION_NEGOTIATE, now.minus(12, ChronoUnit.DAYS));

        Deal d2 = deal(aperture, "Aperture renewal 2026", DealType.RENEWAL, "8268.00", 90,
                LocalDate.now().plusDays(120), "3-year renewal with custom SLA.", sarah.getId(), now);
        stage(d2, DealStageMachine.ACTION_QUALIFY, now.minus(90, ChronoUnit.DAYS));
        stage(d2, DealStageMachine.ACTION_PROPOSE, now.minus(80, ChronoUnit.DAYS));
        stage(d2, DealStageMachine.ACTION_NEGOTIATE, now.minus(60, ChronoUnit.DAYS));
        stage(d2, DealStageMachine.ACTION_WIN, now.minus(50, ChronoUnit.DAYS));
        stage(d2, DealStageMachine.ACTION_MARK_RENEWAL_PENDING, now.minus(30, ChronoUnit.DAYS));

        Deal d3 = deal(brightline, "Brightline renewal 2026", DealType.RENEWAL, "348.00", 55,
                LocalDate.now().plusDays(21), "At risk — champion departed.", david.getId(), now);
        stage(d3, DealStageMachine.ACTION_QUALIFY, now.minus(25, ChronoUnit.DAYS));
        stage(d3, DealStageMachine.ACTION_PROPOSE, now.minus(20, ChronoUnit.DAYS));
        stage(d3, DealStageMachine.ACTION_NEGOTIATE, now.minus(10, ChronoUnit.DAYS));
        stage(d3, DealStageMachine.ACTION_WIN, now.minus(5, ChronoUnit.DAYS));
        stage(d3, DealStageMachine.ACTION_MARK_RENEWAL_PENDING, now.minus(2, ChronoUnit.DAYS));

        Deal d4 = deal(vertex, "Vertex initial subscription", DealType.NEW_BUSINESS, "1194.00", 70,
                LocalDate.now().plusDays(18), "Trial conversion target.", david.getId(), now);
        stage(d4, DealStageMachine.ACTION_QUALIFY, now.minus(15, ChronoUnit.DAYS));
        stage(d4, DealStageMachine.ACTION_PROPOSE, now.minus(8, ChronoUnit.DAYS));

        Deal d5 = deal(helios, "Helios renewal 2026", DealType.RENEWAL, "7868.00", 80,
                LocalDate.now().plusDays(75), "Renewal with seat reduction discussion.", sarah.getId(), now);
        stage(d5, DealStageMachine.ACTION_QUALIFY, now.minus(20, ChronoUnit.DAYS));
        stage(d5, DealStageMachine.ACTION_PROPOSE, now.minus(12, ChronoUnit.DAYS));

        Deal d6 = deal(fjordline, "Fjordline Enterprise upgrade", DealType.NEW_BUSINESS, "4599.00", 35,
                LocalDate.now().plusDays(90), "Next-quarter upgrade conversation.", sarah.getId(), now);
        stage(d6, DealStageMachine.ACTION_QUALIFY, now.minus(6, ChronoUnit.DAYS));

        Deal d7 = deal(brightline, "Brightline seat expansion", DealType.NEW_BUSINESS, "290.00", 25,
                LocalDate.now().plusDays(30), "10 extra seats for CS team.", david.getId(), now);
        stage(d7, DealStageMachine.ACTION_QUALIFY, now.minus(9, ChronoUnit.DAYS));

        Deal d8 = deal(orbital, "Orbital recovery plan", DealType.RENEWAL, "1194.00", 15,
                LocalDate.now().plusDays(200), "On hold pending payment recovery.", david.getId(), now);
        stage(d8, DealStageMachine.ACTION_QUALIFY, now.minus(50, ChronoUnit.DAYS));
        stage(d8, DealStageMachine.ACTION_LOSE, now.minus(30, ChronoUnit.DAYS));
        stage(d8, DealStageMachine.ACTION_RECYCLE, now.minus(5, ChronoUnit.DAYS));

        Deal d9 = deal(quantic, "Quantic wind-down", DealType.RENEWAL, "348.00", 0,
                LocalDate.now().minusDays(40), "Lost with churn.", david.getId(), now);
        stage(d9, DealStageMachine.ACTION_QUALIFY, now.minus(60, ChronoUnit.DAYS));
        stage(d9, DealStageMachine.ACTION_LOSE, now.minus(40, ChronoUnit.DAYS));

        Deal d10 = deal(nimbus, "Nimbus renewal 2026", DealType.RENEWAL, "1549.00", 85,
                LocalDate.now().plusDays(48), "On-track renewal.", sarah.getId(), now);
        stage(d10, DealStageMachine.ACTION_QUALIFY, now.minus(15, ChronoUnit.DAYS));
        stage(d10, DealStageMachine.ACTION_PROPOSE, now.minus(6, ChronoUnit.DAYS));

        Deal d11 = deal(aperture, "Aperture security add-on", DealType.NEW_BUSINESS, "2400.00", 60,
                LocalDate.now().plusDays(60), "Additional compliance module.", sarah.getId(), now);
        stage(d11, DealStageMachine.ACTION_QUALIFY, now.minus(10, ChronoUnit.DAYS));
        stage(d11, DealStageMachine.ACTION_PROPOSE, now.minus(4, ChronoUnit.DAYS));
        stage(d11, DealStageMachine.ACTION_NEGOTIATE, now.minus(1, ChronoUnit.DAYS));

        Deal d12 = deal(vertex, "Vertex data pipeline add-on", DealType.NEW_BUSINESS, "760.00", 20,
                LocalDate.now().plusDays(35), "Early-stage exploration.", david.getId(), now);

        Deal d13 = deal(helios, "Helios 2nd division rollout", DealType.NEW_BUSINESS, "3100.00", 30,
                LocalDate.now().plusDays(100), "Offshore wind division.", sarah.getId(), now);
        stage(d13, DealStageMachine.ACTION_QUALIFY, now.minus(3, ChronoUnit.DAYS));

        Deal d14 = deal(fjordline, "Fjordline renewal 2026", DealType.RENEWAL, "1549.00", 75,
                LocalDate.now().plusDays(160), "Early renewal conversation.", sarah.getId(), now);

        Deal d15 = deal(fjordline, "Fjordline 2025 subscription", DealType.NEW_BUSINESS, "1194.00", 100,
                LocalDate.now().minusDays(200), "Signed 2025 annual subscription.", sarah.getId(), now);
        stage(d15, DealStageMachine.ACTION_QUALIFY, now.minus(240, ChronoUnit.DAYS));
        stage(d15, DealStageMachine.ACTION_PROPOSE, now.minus(230, ChronoUnit.DAYS));
        stage(d15, DealStageMachine.ACTION_NEGOTIATE, now.minus(220, ChronoUnit.DAYS));
        stage(d15, DealStageMachine.ACTION_WIN, now.minus(200, ChronoUnit.DAYS));

        // --- escalations: statuses produced via machine actions ---
        Escalation e1 = escalation(nimbus, "SSO login loop after IdP change", Severity.SEV1,
                "Users cannot complete SAML login since the IdP certificate rotation yesterday; session loops back to the login page.",
                lee.getId(), erin.getId(), now.minus(26, ChronoUnit.HOURS));
        comment(e1, lee, CommentVisibility.INTERNAL, "Reproduced in staging — new certificate is missing the intermediate CA chain.", now.minus(24, ChronoUnit.HOURS));
        comment(e1, lee, CommentVisibility.CUSTOMER_VISIBLE, "We identified a certificate chain issue on our side and are applying the fix now.", now.minus(22, ChronoUnit.HOURS));
        transitionEsc(e1, EscalationStatusMachine.ACTION_WAIT_ON_INTERNAL, now.minus(20, ChronoUnit.HOURS));

        Escalation e2 = escalation(brightline, "Export fails for orgs with >10k rows", Severity.SEV2,
                "CSV export of the full contact list times out after 30 seconds for our largest workspace.",
                maya.getId(), priya.getId(), now.minus(4, ChronoUnit.DAYS));
        comment(e2, maya, CommentVisibility.INTERNAL, "Looks like an N+1 in the export path — will fix in this week's patch.", now.minus(3, ChronoUnit.DAYS));
        comment(e2, maya, CommentVisibility.CUSTOMER_VISIBLE, "We can reproduce the timeout and have scheduled a fix; a workaround is to split the export by team.", now.minus(3, ChronoUnit.DAYS));
        transitionEsc(e2, EscalationStatusMachine.ACTION_WAIT_ON_CUSTOMER, now.minus(2, ChronoUnit.DAYS));
        comment(e2, priya, CommentVisibility.CUSTOMER_VISIBLE, "Workaround works for us, thanks. Please keep us posted on the fix.", now.minus(1, ChronoUnit.DAYS));
        transitionEsc(e2, EscalationStatusMachine.ACTION_CUSTOMER_REPLIED, now.minus(1, ChronoUnit.DAYS));

        Escalation e3 = escalation(aperture, "Webhook deliveries missing for deal events", Severity.SEV2,
                "Our webhook endpoint stopped receiving deal.stage_changed events since Monday 06:00 UTC.",
                null, marcus.getId(), now.minus(3, ChronoUnit.DAYS));
        comment(e3, maya, CommentVisibility.INTERNAL, "Delivery queue was backed up after the deploy; batch retry cleared it. Verify with customer.", now.minus(2, ChronoUnit.DAYS));

        Escalation e4 = escalation(vertex, "Trial workspace blocked on 2FA enrollment", Severity.SEV3,
                "Two of our trial users cannot enroll their authenticators; the QR code scan fails silently.",
                lee.getId(), tomas.getId(), now.minus(50, ChronoUnit.HOURS));
        transitionEsc(e4, EscalationStatusMachine.ACTION_RESOLVE, now.minus(10, ChronoUnit.HOURS));
        comment(e4, lee, CommentVisibility.CUSTOMER_VISIBLE, "The authenticator app needed a clock sync; both users are enrolled now.", now.minus(10, ChronoUnit.HOURS));
        transitionEsc(e4, EscalationStatusMachine.ACTION_CLOSE, now.minus(2, ChronoUnit.HOURS));

        Escalation e5 = escalation(helios, "Invoice PDFs missing VAT details", Severity.SEV2,
                "Invoices rendered after 1 August do not show our VAT number in the footer.",
                maya.getId(), null, now.minus(6, ChronoUnit.DAYS));
        comment(e5, maya, CommentVisibility.INTERNAL, "VAT block depends on org billing address; Helios address was incomplete.", now.minus(5, ChronoUnit.DAYS));
        transitionEsc(e5, EscalationStatusMachine.ACTION_WAIT_ON_INTERNAL, now.minus(4, ChronoUnit.DAYS));

        Escalation e6 = escalation(orbital, "Data export request (account suspended)", Severity.SEV3,
                "We need a full export of our workspace data as part of our internal audit.",
                null, null, now.minus(2, ChronoUnit.DAYS));
        comment(e6, lee, CommentVisibility.INTERNAL, "Account is suspended for non-payment — export requests go through finance before fulfillment.", now.minus(1, ChronoUnit.DAYS));

        Escalation e7 = escalation(fjordline, "API rate limit too low for bulk sync", Severity.SEV3,
                "Our nightly sync needs ~2x the current rate limit; we hit 429s around 03:00 CET.",
                lee.getId(), null, now.minus(30, ChronoUnit.HOURS));
        transitionEsc(e7, EscalationStatusMachine.ACTION_WAIT_ON_INTERNAL, now.minus(20, ChronoUnit.HOURS));

        Escalation e8 = escalation(nimbus, "Billing contact change not reflected", Severity.SEV3,
                "We updated our billing contact last week but invoices still address the old contact.",
                null, erin.getId(), now.minus(5, ChronoUnit.DAYS));
        transitionEsc(e8, EscalationStatusMachine.ACTION_WAIT_ON_CUSTOMER, now.minus(3, ChronoUnit.DAYS));

        Escalation e9 = escalation(brightline, "Dashboard health score mismatch", Severity.SEV3,
                "Our dashboard shows health 64 but the renewal email says 'at risk' — which one is right?",
                maya.getId(), priya.getId(), now.minus(8, ChronoUnit.DAYS));
        comment(e9, maya, CommentVisibility.CUSTOMER_VISIBLE, "Both are correct: 64 is the current score, and scores under 65 are labelled at-risk. Your CSM can walk you through the drivers.", now.minus(7, ChronoUnit.DAYS));
        transitionEsc(e9, EscalationStatusMachine.ACTION_RESOLVE, now.minus(6, ChronoUnit.DAYS));

        Escalation e10 = escalation(aperture, "Custom SLA rider not applied to SEV1", Severity.SEV1,
                "Our contract guarantees 2h SEV1 response; the queue shows 4h SLA for our new escalation.",
                lee.getId(), marcus.getId(), now.minus(5, ChronoUnit.HOURS));
        comment(e10, lee, CommentVisibility.INTERNAL, "Enterprise SLA rider is applied manually until the config feature lands — adjust and notify.", now.minus(4, ChronoUnit.HOURS));
        transitionEsc(e10, EscalationStatusMachine.ACTION_WAIT_ON_INTERNAL, now.minus(2, ChronoUnit.HOURS));

        Escalation e11 = escalation(vertex, "Sandbox environment provisioning", Severity.SEV3,
                "Please provision a second sandbox for our staging team.",
                null, tomas.getId(), now.minus(9, ChronoUnit.DAYS));
        transitionEsc(e11, EscalationStatusMachine.ACTION_WAIT_ON_CUSTOMER, now.minus(7, ChronoUnit.DAYS));
        comment(e11, tomas, CommentVisibility.CUSTOMER_VISIBLE, "Staging team details sent over email — 12 users.", now.minus(1, ChronoUnit.DAYS));
        transitionEsc(e11, EscalationStatusMachine.ACTION_CUSTOMER_REPLIED, now.minus(1, ChronoUnit.DAYS));

        Escalation e12 = escalation(helios, "Role permissions too coarse for auditors", Severity.SEV3,
                "Our auditors need read-only access to invoices without seeing support tickets.",
                maya.getId(), null, now.minus(12, ChronoUnit.DAYS));
        transitionEsc(e12, EscalationStatusMachine.ACTION_RESOLVE, now.minus(8, ChronoUnit.DAYS));
        transitionEsc(e12, EscalationStatusMachine.ACTION_CLOSE, now.minus(7, ChronoUnit.DAYS));

        Escalation e13 = escalation(fjordline, "Uploaded documents missing from portal", Severity.SEV2,
                "Two onboarding documents uploaded by your team do not appear in our portal documents tab.",
                lee.getId(), null, now.minus(2, ChronoUnit.HOURS));
        comment(e13, lee, CommentVisibility.INTERNAL, "Both were marked INTERNAL visibility — need customer-visible copies.", now.minus(1, ChronoUnit.HOURS));

        Escalation e14 = escalation(orbital, "Suspended account — request to settle outstanding balance", Severity.SEV2,
                "We want to clear the overdue balance and re-activate; need the payment instructions.",
                null, null, now.minus(4, ChronoUnit.HOURS));
        transitionEsc(e14, EscalationStatusMachine.ACTION_WAIT_ON_INTERNAL, now.minus(3, ChronoUnit.HOURS));

        Escalation e15 = escalation(nimbus, "API key last-used column always empty", Severity.SEV3,
                "In the admin UI the 'last used' column for our API keys is empty even though we call the API daily.",
                null, erin.getId(), now.minus(20, ChronoUnit.HOURS));

        // --- documents: 13 across categories and visibility levels ---
        doc(nimbus, "Nimbus Cloud MSA 2026.pdf", DocCategory.CONTRACT, DocVisibility.CUSTOMER_VISIBLE,
                "application/pdf", "Signed master services agreement.", sarah.getId(), now.minus(200, ChronoUnit.DAYS));
        doc(nimbus, "Nimbus security questionnaire Q2.pdf", DocCategory.SECURITY_QUESTIONNAIRE, DocVisibility.INTERNAL,
                "application/pdf", "SOC2 questionnaire response (internal review copy).", sarah.getId(), now.minus(90, ChronoUnit.DAYS));
        doc(nimbus, "Nimbus invoice INV-2026-0912.pdf", DocCategory.INVOICE, DocVisibility.BILLING_ONLY,
                "application/pdf", "September invoice.", sarah.getId(), now.minus(5, ChronoUnit.DAYS));
        doc(aperture, "Aperture renewal proposal.pdf", DocCategory.CONTRACT, DocVisibility.INTERNAL,
                "application/pdf", "Draft 3-year renewal with SLA rider.", sarah.getId(), now.minus(20, ChronoUnit.DAYS));
        doc(aperture, "Aperture security review 2026.pdf", DocCategory.SECURITY_QUESTIONNAIRE, DocVisibility.CUSTOMER_VISIBLE,
                "application/pdf", "Published security review.", sarah.getId(), now.minus(120, ChronoUnit.DAYS));
        doc(brightline, "Brightline MSA.pdf", DocCategory.CONTRACT, DocVisibility.CUSTOMER_VISIBLE,
                "application/pdf", "Signed MSA.", david.getId(), now.minus(300, ChronoUnit.DAYS));
        doc(brightline, "Brightline onboarding checklist.txt", DocCategory.ONBOARDING, DocVisibility.CUSTOMER_VISIBLE,
                "text/plain", "Onboarding checklist shared with the customer.", david.getId(), now.minus(250, ChronoUnit.DAYS));
        doc(brightline, "Brightline overdue invoice notes.txt", DocCategory.INVOICE, DocVisibility.BILLING_ONLY,
                "text/plain", "Internal billing notes for the overdue June invoice.", david.getId(), now.minus(60, ChronoUnit.DAYS));
        doc(vertex, "Vertex trial terms.pdf", DocCategory.CONTRACT, DocVisibility.CUSTOMER_VISIBLE,
                "application/pdf", "Trial terms and conditions.", david.getId(), now.minus(100, ChronoUnit.DAYS));
        doc(helios, "Helios MSA + addendum.pdf", DocCategory.CONTRACT, DocVisibility.CUSTOMER_VISIBLE,
                "application/pdf", "Signed MSA with data-residency addendum.", sarah.getId(), now.minus(400, ChronoUnit.DAYS));
        doc(helios, "Helios invoice INV-2026-0914.pdf", DocCategory.INVOICE, DocVisibility.BILLING_ONLY,
                "application/pdf", "September invoice.", sarah.getId(), now.minus(6, ChronoUnit.DAYS));
        doc(fjordline, "Fjordline onboarding guide.txt", DocCategory.ONBOARDING, DocVisibility.CUSTOMER_VISIBLE,
                "text/plain", "Getting-started guide for the Fjordline workspace.", sarah.getId(), now.minus(30, ChronoUnit.DAYS));
        doc(orbital, "Orbital collection correspondence.txt", DocCategory.OTHER, DocVisibility.INTERNAL,
                "text/plain", "Finance correspondence re: outstanding balance.", david.getId(), now.minus(20, ChronoUnit.DAYS));

        // --- API keys: active, revoked, expired — each via the state machine ---
        seedApiKey(nimbus, "Nimbus production", ApiKeyStatus.ACTIVE, now, now.plus(365, ChronoUnit.DAYS), sarah.getId());
        seedApiKey(aperture, "Aperture CI", ApiKeyStatus.ACTIVE, now, now.plus(180, ChronoUnit.DAYS), sarah.getId());
        seedApiKey(brightline, "Brightline legacy", ApiKeyStatus.REVOKED, now.minus(200, ChronoUnit.DAYS),
                now.minus(100, ChronoUnit.DAYS), david.getId(), true);
        seedApiKey(vertex, "Vertex sandbox", ApiKeyStatus.EXPIRED, now.minus(300, ChronoUnit.DAYS),
                now.minus(100, ChronoUnit.DAYS), david.getId(), false);
        seedApiKey(helios, "Helios integration", ApiKeyStatus.ACTIVE, now, now.plus(90, ChronoUnit.DAYS), sarah.getId());

        // --- webhooks (3), pointed at the core-mock webhook sink for the local demo ---
        String sink = properties.webhookSinkBase();
        webhook(nimbus, sink + "/mock/webhook-sink/nimbus",
                "Nimbus production webhook", "escalation.created,escalation.updated,escalation.resolved,document.uploaded",
                "nimbus-webhook-secret-demo", now);
        webhook(aperture, sink + "/mock/webhook-sink/aperture",
                "Aperture ops webhook", "escalation.resolved,deal.stage_changed",
                "aperture-webhook-secret-demo", now);
        webhook(brightline, sink + "/mock/webhook-sink/brightline",
                "Brightline all events", "escalation.created,escalation.updated,escalation.resolved,document.uploaded,deal.stage_changed,renewal.due",
                "brightline-webhook-secret-demo", now);

        // Notifications for the demo dashboards.
        notifications.notify(sarah.getId(), "renewal", "Renewal due soon: Brightline",
                "Brightline renews in " + (21) + " days — renewal risk assessment attached.", "organizations/3");
        notifications.notify(david.getId(), "renewal", "Renewal due soon: Brightline",
                "Brightline renews in " + (21) + " days — renewal risk assessment attached.", "organizations/3");
        notifications.notify(sarah.getId(), "deal", "Deal stage changed: Aperture security add-on",
                "Aperture security add-on moved to Negotiation.", "organizations/2");
        notifications.notify(lee.getId(), "escalation", "SEV1 escalation assigned: SSO login loop",
                "Nimbus Cloud · SEV1 · Critical", "escalations/1");
        notifications.notify(erin.getId(), "escalation", "Support update on SSO login loop",
                "We identified a certificate chain issue on our side and are applying the fix now.", "escalations/1");
        notifications.notify(maya.getId(), "escalation", "Customer replied: Export fails for orgs with >10k rows",
                "Brightline", "escalations/2");
        notifications.notify(admin.getId(), "system", "Demo workspace seeded",
                "The demo dataset is ready: 8 organizations, 22 contacts, 14 deals, 15 escalations.", null);

        markerRepo.save(new SeedMarker(SEED_KEY, Instant.now().toString()));
        // Push the freshly created password hashes first (users-only sync), so
        // decision can authenticate the demo accounts, then the full snapshot.
        for (var entry : seededHashes.entrySet()) {
            try {
                sync.syncUserWithPassword(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("could not push seed password for {}: {}", entry.getKey().getUsername(), e.getMessage());
                sync.markDirty();
            }
        }
        try {
            sync.fullSync();
        } catch (Exception e) {
            log.warn("decision tier not reachable during seed; sync will retry: {}", e.getMessage());
            sync.markDirty();
        }
        log.info("seed complete: demo users admin/sarah.chan/david.okafor/lee.support/maya.support/erin.foley/marcus.webb/priya.nair/tomas.reyes");
    }

    private UserAccount user(long id, String username, String displayName, String email, Role role,
                             Long customerOrgId, String title, String password) {
        UserAccount user = new UserAccount(id, username, displayName, email, role, true, customerOrgId,
                title, null, Instant.now());
        userRepo.save(user);
        if (password != null) {
            // Seed hashes are pushed to the decision tier in the final sync
            // (users carry no hash column in the channel database).
            seededHashes.put(user, passwordEncoder.encode(password));
        }
        return user;
    }

    private final Map<UserAccount, String> seededHashes = new java.util.HashMap<>();

    private Organization org(long id, String name, Plan plan, OrgStatus status, Long ownerUserId,
                             LocalDate renewalDate, int healthScore, String healthRationale,
                             String industry, String country, Integer employees, int seats, String website,
                             String internalNotes, Instant now) {
        Organization org = new Organization(name, plan, status, ownerUserId, renewalDate, healthScore,
                healthRationale, industry, country, employees, seats, website, internalNotes, now);
        orgRepo.save(org);
        return org;
    }

    private void assign(UserAccount user, List<Organization> orgs) {
        for (Organization org : orgs) {
            assignmentRepo.save(new OrgAssignment(user.getId(), org.getId(), AssignmentRelation.OWNER));
        }
    }

    private void contact(Organization org, String firstName, String lastName, String email, String phone,
                         String title, String permissions, boolean billing, boolean primary, Long userId, Instant now) {
        contactRepo.save(new Contact(org, firstName, lastName, email, phone, title, permissions, billing,
                primary, userId, now));
    }

    private Deal deal(Organization org, String name, DealType type, String amount, int probability,
                      LocalDate closeDate, String notes, Long ownerUserId, Instant now) {
        Deal deal = new Deal(org, name, type, DealStage.LEAD, new BigDecimal(amount), "USD", probability,
                closeDate, notes, ownerUserId, now);
        dealRepo.save(deal);
        return deal;
    }

    /** Applies a stage transition through the machine and audits it, like the UI does. */
    private void stage(Deal deal, String action, Instant when) {
        if (!DealStageMachine.isAllowed(deal.getStage(), action)) {
            log.warn("seed skipped invalid deal transition {} from {}", action, deal.getStage());
            return;
        }
        DealStage before = deal.getStage();
        deal.moveTo(DealStageMachine.target(deal.getStage(), action), when);
        dealRepo.save(deal);
        audit.record("deal.stage_change", "deal", deal.getId(), deal.getOrg().getId(),
                Map.of("stage", before.name()), Map.of("stage", deal.getStage().name()),
                "SYSTEM:seeder", false);
    }

    private Escalation escalation(Organization org, String subject, Severity severity, String description,
                                  Long assigneeId, Long createdBy, Instant createdAt) {
        Escalation escalation = new Escalation(org, subject, description, severity, EscalationStatus.OPEN,
                assigneeId, createdBy, createdAt);
        escalationRepo.save(escalation);
        return escalation;
    }

    private void transitionEsc(Escalation escalation, String action, Instant when) {
        if (!EscalationStatusMachine.isAllowed(escalation.getStatus(), action)) {
            log.warn("seed skipped invalid escalation transition {} from {}", action, escalation.getStatus());
            return;
        }
        EscalationStatus before = escalation.getStatus();
        escalation.moveTo(EscalationStatusMachine.target(escalation.getStatus(), action), when);
        escalationRepo.save(escalation);
        audit.record("escalation.status_change", "escalation", escalation.getId(), escalation.getOrg().getId(),
                Map.of("status", before.name()), Map.of("status", escalation.getStatus().name()),
                "SYSTEM:seeder", true);
    }

    private void comment(Escalation escalation, UserAccount author, CommentVisibility visibility, String body,
                         Instant when) {
        commentRepo.save(new EscalationComment(escalation, author.getId(), author.getRole().name(), visibility,
                body, when));
    }

    private void doc(Organization org, String name, DocCategory category, DocVisibility visibility,
                     String contentType, String description, Long uploadedBy, Instant uploadedAt) {
        String storedName = java.util.UUID.randomUUID() + extensionOf(name);
        byte[] content = sampleFileContent(name, contentType);
        try {
            fileStorage.store(storedName, content);
        } catch (Exception e) {
            log.warn("seed could not store sample file {}: {}", name, e.getMessage());
        }
        DocumentMeta meta = new DocumentMeta(org, name, storedName, category, visibility, contentType,
                content.length, description, uploadedBy, uploadedAt);
        docRepo.save(meta);
    }

    private void seedApiKey(Organization org, String name, ApiKeyStatus status, Instant createdAt,
                            Instant expiresAt, Long createdBy) {
        seedApiKey(org, name, status, createdAt, expiresAt, createdBy, false);
    }

    private void seedApiKey(Organization org, String name, ApiKeyStatus status, Instant createdAt,
                            Instant expiresAt, Long createdBy, boolean revokeAfterCreate) {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        String secret = "crm_live_" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        com.crmops.channel.domain.ApiKey key = new com.crmops.channel.domain.ApiKey(org, name,
                ApiKeyService.sha256(secret), secret.substring(0, 12), secret.substring(secret.length() - 4),
                ApiKeyStatus.ACTIVE, createdAt, expiresAt, createdBy);
        apiKeyRepo.save(key);
        log.info("seeded api key '{}' (org {}): {} — shown once, stored hashed", name, org.getName(), secret);
        if (revokeAfterCreate) {
            ApiKeyStateMachine.requireAllowed(key.getStatus(), ApiKeyStateMachine.ACTION_REVOKE);
            key.revoke(createdAt.plus(50, ChronoUnit.DAYS));
        } else if (status == ApiKeyStatus.EXPIRED) {
            ApiKeyStateMachine.requireAllowed(key.getStatus(), ApiKeyStateMachine.ACTION_EXPIRE);
            key.expire(createdAt.plus(100, ChronoUnit.DAYS));
        }
        apiKeyRepo.save(key);
    }

    private void webhook(Organization org, String url, String description, String events, String secret, Instant now) {
        webhookRepo.save(new WebhookConfig(org, url, description, events, secret, WebhookConfigStatus.ACTIVE, now));
    }

    private static byte[] sampleFileContent(String name, String contentType) {
        String body;
        if (contentType.startsWith("text/")) {
            body = "CRMops demo document\n====================\n\nFile: " + name
                    + "\nThis is a generated sample file used to exercise the document workflow.\n"
                    + "It contains no real customer data.\n";
            return body.getBytes(StandardCharsets.UTF_8);
        }
        // Minimal valid one-page PDF.
        body = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                + "4 0 obj<</Length 90>>stream\nBT /F1 14 Tf 72 720 Td (CRMops demo document - " + name.replace(")", "") + ") Tj ET\nendstream\nendobj\n"
                + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n"
                + "xref\n0 6\n0000000000 65535 f \ntrailer<</Size 6/Root 1 0 R>>\nstartxref\n%%EOF\n";
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot);
    }
}
