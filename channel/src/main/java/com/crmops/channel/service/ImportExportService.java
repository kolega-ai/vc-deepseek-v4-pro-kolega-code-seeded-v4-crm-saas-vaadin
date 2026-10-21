package com.crmops.channel.service;

import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Plan;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.repo.PlanRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.IMPORT_RUN;

/**
 * CSV import for contacts and organizations. Every row is validated with
 * row-level error reporting; the import either applies fully or reports all
 * problems (dry-run). One audit event per import names the record type and the
 * number of rows imported.
 */
@Service
public class ImportExportService {

    private final ContactService contactService;
    private final OrganizationService orgService;
    private final ContactRepository contactRepo;
    private final PlanRepository planRepo;
    private final CrmAuthorization authz;
    private final AuditService audit;

    @PersistenceContext
    private EntityManager entityManager;

    public ImportExportService(ContactService contactService, OrganizationService orgService,
                               ContactRepository contactRepo, PlanRepository planRepo,
                               CrmAuthorization authz, AuditService audit) {
        this.contactService = contactService;
        this.orgService = orgService;
        this.contactRepo = contactRepo;
        this.planRepo = planRepo;
        this.authz = authz;
        this.audit = audit;
    }

    public record ImportResult(int totalRows, int importedRows, List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** Contacts CSV: first_name,last_name,email,phone,title,permissions,billing,primary */
    @Transactional
    public ImportResult importContacts(long orgId, byte[] csvBytes, boolean dryRun) {
        var verdict = authz.require(IMPORT_RUN);
        orgService.get(orgId); // org access
        List<ContactRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int rowNumber = 2; // header is row 1
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(new String(csvBytes, StandardCharsets.UTF_8)))) {
            var header = parser.getHeaderMap();
            for (String required : List.of("first_name", "last_name", "email")) {
                if (header == null || !header.containsKey(required)) {
                    errors.add("missing column: " + required);
                }
            }
            for (CSVRecord record : parser) {
                ContactRow row = parseContactRow(record, rowNumber);
                if (row.error != null) {
                    errors.add("row " + rowNumber + ": " + row.error);
                } else if (contactRepo.findByOrgIdAndEmailIgnoreCase(orgId, row.email).isPresent()) {
                    errors.add("row " + rowNumber + ": duplicate email " + row.email);
                } else {
                    rows.add(row);
                }
                rowNumber++;
            }
        } catch (IOException | IllegalArgumentException e) {
            errors.add("cannot parse CSV: " + e.getMessage());
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add("no data rows found");
        }
        if (dryRun) {
            return new ImportResult(rowNumber - 2, 0, errors);
        }
        int imported = 0;
        if (errors.isEmpty()) {
            for (ContactRow row : rows) {
                contactService.create(orgId, row.firstName, row.lastName, row.email, row.phone, row.title,
                        row.permissions, row.billing, row.primary);
                imported++;
            }
            audit.record("import.run", "contact", null, orgId, null,
                    Map.of("count", imported, "orgId", orgId), verdict.authorizationBasis(), false);
        }
        return new ImportResult(rowNumber - 2, imported, errors);
    }

    /** Partner contact feed (XML): &lt;contacts&gt;&lt;contact first_name=… /&gt;&lt;/contacts&gt; */
    @Transactional
    public ImportResult importContactsXml(long orgId, byte[] xmlBytes, boolean dryRun) {
        var verdict = authz.require(IMPORT_RUN);
        orgService.get(orgId); // org access
        List<ContactRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            parser.parse(new ByteArrayInputStream(xmlBytes), new ContactFeedHandler(rows, errors));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            errors.add("cannot parse XML: " + e.getMessage());
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add("no contact elements found");
        }
        if (dryRun) {
            return new ImportResult(rows.size(), 0, errors);
        }
        int imported = 0;
        if (errors.isEmpty()) {
            for (ContactRow row : rows) {
                if (contactRepo.findByOrgIdAndEmailIgnoreCase(orgId, row.email).isPresent()) {
                    errors.add("duplicate email " + row.email);
                    continue;
                }
                contactService.create(orgId, row.firstName, row.lastName, row.email, row.phone, row.title,
                        row.permissions, row.billing, row.primary);
                imported++;
            }
            audit.record("import.run", "contact", null, orgId, null,
                    Map.of("count", imported, "orgId", orgId, "format", "xml"),
                    verdict.authorizationBasis(), false);
        }
        return new ImportResult(rows.size(), imported, errors);
    }

    /** Organizations CSV: name,plan_code,industry,country,employees,seats,website,renewal_date */
    @Transactional
    public ImportResult importOrganizations(byte[] csvBytes, boolean dryRun) {
        var verdict = authz.require(IMPORT_RUN);
        List<OrgRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int rowNumber = 2;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(new String(csvBytes, StandardCharsets.UTF_8)))) {
            var header = parser.getHeaderMap();
            for (String required : List.of("name", "plan_code")) {
                if (header == null || !header.containsKey(required)) {
                    errors.add("missing column: " + required);
                }
            }
            for (CSVRecord record : parser) {
                OrgRow row = parseOrgRow(record, rowNumber);
                if (row.error != null) {
                    errors.add("row " + rowNumber + ": " + row.error);
                } else {
                    rows.add(row);
                }
                rowNumber++;
            }
        } catch (IOException | IllegalArgumentException e) {
            errors.add("cannot parse CSV: " + e.getMessage());
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add("no data rows found");
        }
        if (dryRun) {
            return new ImportResult(rowNumber - 2, 0, errors);
        }
        int imported = 0;
        if (errors.isEmpty()) {
            for (OrgRow row : rows) {
                // Account managers auto-own the orgs they import; the OWNER
                // assignment is what grants them access through decision.
                Organization org = orgService.create(row.name, row.planId, row.industry, row.country,
                        row.employees, row.seats, row.website, row.renewalDate, null);
                if (!SecurityUtils.requireUser().isSystem() && org.getOwnerUserId() == null
                        && SecurityUtils.requireUser().role() == com.crmops.channel.domain.Role.ACCOUNT_MANAGER) {
                    orgService.setOwner(org.getId(), SecurityUtils.requireUser().userId());
                }
                imported++;
            }
            audit.record("import.run", "organization", null, null, null,
                    Map.of("count", imported), verdict.authorizationBasis(), false);
        }
        return new ImportResult(rowNumber - 2, imported, errors);
    }

    /**
     * Partner pre-flight: given an uploaded reference list of contact emails,
     * reports which of them already exist for the organization so a bulk import
     * can skip the duplicates.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> matchContacts(long orgId, byte[] csvBytes) {
        authz.require(IMPORT_RUN);
        orgService.get(orgId); // org access
        List<String> refs = new ArrayList<>();
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).build()
                .parse(new StringReader(new String(csvBytes, StandardCharsets.UTF_8)))) {
            for (CSVRecord record : parser) {
                String email = record.isMapped("email") ? record.get("email").trim() : record.get(0).trim();
                if (!email.isEmpty()) {
                    refs.add(email);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ValidationException("cannot parse the reference list: " + e.getMessage());
        }
        if (refs.isEmpty()) {
            return List.of();
        }
        String inList = String.join(", ", refs.stream().map(r -> "'" + r + "'").toList());
        String sql = "select id, email from contacts where org_id = " + orgId + " and email in (" + inList + ")";
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
        List<Map<String, Object>> found = new ArrayList<>();
        for (Object[] row : rows) {
            found.add(Map.of("id", row[0], "email", row[1]));
        }
        return found;
    }

    private ContactRow parseContactRow(CSVRecord record, int rowNumber) {
        String firstName = record.isMapped("first_name") ? record.get("first_name") : "";
        String lastName = record.isMapped("last_name") ? record.get("last_name") : "";
        String email = record.isMapped("email") ? record.get("email") : "";
        try {
            ContactService.validate(firstName, lastName, email);
        } catch (ValidationException e) {
            return ContactRow.error(e.getMessage());
        }
        boolean billing = record.isMapped("billing") && "true".equalsIgnoreCase(record.get("billing").trim());
        boolean primary = record.isMapped("primary") && "true".equalsIgnoreCase(record.get("primary").trim());
        return new ContactRow(firstName.trim(), lastName.trim(), email.trim(),
                record.isMapped("phone") ? record.get("phone") : null,
                record.isMapped("title") ? record.get("title") : null,
                record.isMapped("permissions") ? record.get("permissions") : null,
                billing, primary, null);
    }

    private OrgRow parseOrgRow(CSVRecord record, int rowNumber) {
        String name = record.isMapped("name") ? record.get("name").trim() : "";
        String planCode = record.isMapped("plan_code") ? record.get("plan_code").trim().toUpperCase() : "";
        if (name.isEmpty() || name.length() > 160) {
            return OrgRow.error("organization name is required (max 160 characters)");
        }
        Plan plan = planRepo.findByCode(planCode).orElse(null);
        if (plan == null) {
            return OrgRow.error("unknown plan_code '" + planCode + "' (STARTER, PRO or ENTERPRISE)");
        }
        Integer employees = null;
        if (record.isMapped("employees") && !record.get("employees").isBlank()) {
            try {
                employees = Integer.parseInt(record.get("employees").trim());
            } catch (NumberFormatException e) {
                return OrgRow.error("employees must be a number");
            }
        }
        int seats = 10;
        if (record.isMapped("seats") && !record.get("seats").isBlank()) {
            try {
                seats = Integer.parseInt(record.get("seats").trim());
            } catch (NumberFormatException e) {
                return OrgRow.error("seats must be a number");
            }
        }
        if (seats < 1 || seats > 100_000) {
            return OrgRow.error("seats must be between 1 and 100000");
        }
        LocalDate renewal = null;
        if (record.isMapped("renewal_date") && !record.get("renewal_date").isBlank()) {
            try {
                renewal = LocalDate.parse(record.get("renewal_date").trim());
            } catch (DateTimeParseException e) {
                return OrgRow.error("renewal_date must be YYYY-MM-DD");
            }
        }
        return new OrgRow(name, plan.getId(), record.isMapped("industry") ? record.get("industry") : null,
                record.isMapped("country") ? record.get("country") : null, employees, seats,
                record.isMapped("website") ? record.get("website") : null, renewal, null);
    }

    /** Example CSV template for download from the import screens. */
    public byte[] contactsTemplate() {
        return ("first_name,last_name,email,phone,title,permissions,billing,primary\n"
                + "Ada,Lovelace,ada@example.com,+1 415 555 0101,VP Engineering,TECHNICAL_CONTACT,false,false\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public byte[] organizationsTemplate() {
        return ("name,plan_code,industry,country,employees,seats,website,renewal_date\n"
                + "Example Corp,STARTER,Manufacturing,Germany,120,12,https://example.com,2027-01-31\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Reads &lt;contact/&gt; elements of a partner feed into contact rows. */
    private static final class ContactFeedHandler extends DefaultHandler {

        private final List<ContactRow> rows;
        private final List<String> errors;
        private int index;

        ContactFeedHandler(List<ContactRow> rows, List<String> errors) {
            this.rows = rows;
            this.errors = errors;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (!"contact".equalsIgnoreCase(qName)) {
                return;
            }
            index++;
            String firstName = value(attributes, "first_name");
            String lastName = value(attributes, "last_name");
            String email = value(attributes, "email");
            try {
                ContactService.validate(firstName, lastName, email);
            } catch (ValidationException e) {
                errors.add("contact " + index + ": " + e.getMessage());
                return;
            }
            rows.add(new ContactRow(firstName.trim(), lastName.trim(), email.trim(),
                    value(attributes, "phone"), value(attributes, "title"),
                    value(attributes, "permissions"),
                    "true".equalsIgnoreCase(value(attributes, "billing")),
                    "true".equalsIgnoreCase(value(attributes, "primary")), null));
        }

        private static String value(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            return value == null ? "" : value;
        }
    }

    private record ContactRow(String firstName, String lastName, String email, String phone, String title,
                              String permissions, boolean billing, boolean primary, String error) {
        static ContactRow error(String message) {
            return new ContactRow(null, null, null, null, null, null, false, false, message);
        }
    }

    private record OrgRow(String name, Long planId, String industry, String country, Integer employees,
                          int seats, String website, LocalDate renewalDate, String error) {
        static OrgRow error(String message) {
            return new OrgRow(null, null, null, null, null, 0, null, null, message);
        }
    }
}
