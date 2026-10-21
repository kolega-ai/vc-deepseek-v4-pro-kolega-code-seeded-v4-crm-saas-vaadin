package com.crmops.channel.service;

import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.domain.WebhookConfig;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.repo.WebhookConfigRepository;
import com.crmops.channel.repo.UserAccountRepository;
import com.crmops.channel.security.CrmAuthorization;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.EXPORT_RUN;

/**
 * CSV reports. Every export is audited with ONE audit event per request naming
 * the resource class and the count returned (never one event per record), and
 * every row is scoped by the decision tier. Internal notes and internal
 * escalation comments never appear in any export.
 */
@Service
public class ReportService {

    private final OrganizationService orgService;
    private final EscalationService escalationService;
    private final ContactRepository contactRepo;
    private final WebhookConfigRepository webhookRepo;
    private final UserAccountRepository userRepo;
    private final CrmAuthorization authz;
    private final AuditService audit;

    public ReportService(OrganizationService orgService, EscalationService escalationService,
                         ContactRepository contactRepo, WebhookConfigRepository webhookRepo,
                         UserAccountRepository userRepo, CrmAuthorization authz, AuditService audit) {
        this.orgService = orgService;
        this.escalationService = escalationService;
        this.contactRepo = contactRepo;
        this.webhookRepo = webhookRepo;
        this.userRepo = userRepo;
        this.authz = authz;
        this.audit = audit;
    }

    public record Report(String fileName, String contentType, byte[] content) {
    }

    /** Renewals-at-risk report: organizations renewing within the next 30 days. */
    @Transactional
    public Report renewalsReport() {
        var verdict = authz.require(EXPORT_RUN);
        LocalDate today = LocalDate.now();
        List<Organization> orgs = orgService.renewalsDue(today, today.plusDays(30));
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder()
                .setHeader("organization", "plan", "status", "owner", "renewal_date", "health_score",
                        "health_bucket", "seats", "mrr_usd", "open_escalations").build())) {
            for (Organization org : orgs) {
                long open = escalationService.listByOrg(org.getId()).stream()
                        .filter(e -> switch (e.getStatus()) {
                            case OPEN, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL -> true;
                            default -> false;
                        })
                        .count();
                printer.printRecord(
                        org.getName(), org.getPlan().getCode(), org.getStatus().name(),
                        ownerName(org.getOwnerUserId()), org.getRenewalDate(), org.getHealthScore(),
                        org.healthBucket(), org.getSeats(),
                        OrganizationService.mrr(org).toPlainString(), open);
            }
        } catch (IOException e) {
            throw new IllegalStateException("csv generation failed", e);
        }
        audit.record("export.run", "organization", null, null, null,
                Map.of("report", "renewals", "count", orgs.size()), verdict.authorizationBasis(), false);
        return new Report("renewals-at-risk.csv", "text/csv", out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Customer health report across the caller's scope. */
    @Transactional
    public Report healthReport() {
        var verdict = authz.require(EXPORT_RUN);
        List<Organization> orgs = orgService.search(new OrganizationQueryService.SearchCriteria(
                null, null, null, null, null, "healthScore", "desc"));
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder()
                .setHeader("organization", "plan", "status", "owner", "health_score", "health_bucket",
                        "renewal_date", "mrr_usd", "health_rationale").build())) {
            for (Organization org : orgs) {
                printer.printRecord(
                        org.getName(), org.getPlan().getCode(), org.getStatus().name(),
                        ownerName(org.getOwnerUserId()), org.getHealthScore(), org.healthBucket(),
                        org.getRenewalDate(), OrganizationService.mrr(org).toPlainString(),
                        org.getHealthRationale() == null ? "" : org.getHealthRationale());
            }
        } catch (IOException e) {
            throw new IllegalStateException("csv generation failed", e);
        }
        audit.record("export.run", "organization", null, null, null,
                Map.of("report", "health", "count", orgs.size()), verdict.authorizationBasis(), false);
        return new Report("customer-health.csv", "text/csv", out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Escalation report. Internal comments are excluded from the export. */
    @Transactional
    public Report escalationReport() {
        var verdict = authz.require(EXPORT_RUN);
        List<Escalation> rows = escalationService.reportRows();
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder()
                .setHeader("id", "organization", "subject", "severity", "status", "assignee",
                        "created_at", "sla_due_at", "sla_breached", "resolved_at").build())) {
            for (Escalation escalation : rows) {
                printer.printRecord(
                        escalation.getId(), escalation.getOrg().getName(), escalation.getSubject(),
                        escalation.getSeverity().name(), escalation.getStatus().name(),
                        assigneeName(escalation.getAssigneeId()), escalation.getCreatedAt(),
                        escalation.getSlaDueAt(), escalation.isSlaBreached(), escalation.getResolvedAt());
            }
        } catch (IOException e) {
            throw new IllegalStateException("csv generation failed", e);
        }
        audit.record("export.run", "escalation", null, null, null,
                Map.of("report", "escalations", "count", rows.size()), verdict.authorizationBasis(), false);
        return new Report("escalations.csv", "text/csv", out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Contact export for an organization (customer-visible data only). */
    @Transactional
    public Report contactsReport(long orgId) {
        var verdict = authz.require(EXPORT_RUN);
        orgService.get(orgId);
        List<Contact> contacts = contactRepo.findByOrgIdOrderByPrimaryDescLastNameAsc(orgId);
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder()
                .setHeader("first_name", "last_name", "email", "phone", "title", "permissions",
                        "billing", "primary").build())) {
            for (Contact contact : contacts) {
                printer.printRecord(contact.getFirstName(), contact.getLastName(), contact.getEmail(),
                        contact.getPhone() == null ? "" : contact.getPhone(),
                        contact.getTitle() == null ? "" : contact.getTitle(),
                        contact.getPermissions() == null ? "" : contact.getPermissions(),
                        contact.isBilling(), contact.isPrimary());
            }
        } catch (IOException e) {
            throw new IllegalStateException("csv generation failed", e);
        }
        audit.record("export.run", "contact", null, orgId, null,
                Map.of("report", "contacts", "count", contacts.size()), verdict.authorizationBasis(), false);
        return new Report("contacts-" + orgId + ".csv", "text/csv", out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Integration endpoint inventory used for operations handover. */
    @Transactional
    public Report integrationsReport() {
        var verdict = authz.require(EXPORT_RUN);
        List<Organization> orgs = orgService.search(new OrganizationQueryService.SearchCriteria(
                null, null, null, null, null, "name", "asc"));
        StringWriter out = new StringWriter();
        int rows = 0;
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder()
                .setHeader("organization", "endpoint", "events", "status", "signing_key", "created_at").build())) {
            for (Organization org : orgs) {
                for (WebhookConfig config : webhookRepo.findByOrgIdOrderByCreatedAtDesc(org.getId())) {
                    printer.printRecord(org.getName(), config.getUrl(), config.getEvents(),
                            config.getStatus().name(), config.getSecret(), config.getCreatedAt());
                    rows++;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("csv generation failed", e);
        }
        audit.record("export.run", "webhook_config", null, null, null,
                Map.of("report", "integrations", "count", rows), verdict.authorizationBasis(), false);
        return new Report("integrations.csv", "text/csv",
                out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String ownerName(Long ownerUserId) {
        if (ownerUserId == null) {
            return "";
        }
        return userRepo.findById(ownerUserId).map(UserAccount::getDisplayName).orElse("");
    }

    private String assigneeName(Long assigneeId) {
        if (assigneeId == null) {
            return "";
        }
        return userRepo.findById(assigneeId).map(UserAccount::getDisplayName).orElse("");
    }
}
