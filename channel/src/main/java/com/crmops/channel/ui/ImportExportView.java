package com.crmops.channel.ui;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.ImportExportService;
import com.crmops.channel.service.OrganizationQueryService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.ReportService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * CSV import/export workflows. Imports validate every row and report all
 * problems; exports stream server-generated CSV through dynamic resources so
 * every download is scoped and audited by the decision tier.
 */
@Route("admin/import-export")
@RouteAccess({Role.PLATFORM_ADMIN})
@PageTitle("Import & export · CrmOps")
public class ImportExportView extends VerticalLayout {

    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;

    private final ImportExportService importExportService;
    private final ReportService reportService;
    private final OrganizationService orgService;

    private final List<Organization> organizations = new ArrayList<>();
    private final Select<Organization> contactOrgSelect = new Select<>();
    private final Select<Organization> exportOrgSelect = new Select<>();
    private final Button runContactImport = Ui.primary("Import contacts");
    private final Button runOrgImport = Ui.primary("Import organizations");
    private final Div anchorHost = new Div();
    private byte[] contactsCsv;
    private String contactsFileName;
    private byte[] orgsCsv;

    public ImportExportView(ImportExportService importExportService, ReportService reportService,
                            OrganizationService orgService) {
        this.importExportService = importExportService;
        this.reportService = reportService;
        this.orgService = orgService;
        addClassName("page");
        setPadding(false);
        setWidthFull();

        add(Ui.pageHeader("Import & export", "CSV workflows for contacts, organizations and reports."));

        anchorHost.getStyle().set("display", "none");
        add(anchorHost);

        try {
            organizations.addAll(orgService.search(new OrganizationQueryService.SearchCriteria(
                    null, null, null, null, null, "name", "asc")));
        } catch (RuntimeException e) {
            fail(e);
        }

        add(importContactsCard());
        add(importOrganizationsCard());
        add(exportsCard());
    }

    private Div importContactsCard() {
        Div card = Ui.card();
        Span title = new Span("Import contacts");
        title.addClassName("card-title");
        card.add(title);

        contactOrgSelect.setLabel("Organization");
        contactOrgSelect.setItems(organizations);
        contactOrgSelect.setItemLabelGenerator(Organization::getName);
        contactOrgSelect.getElement().setAttribute("aria-label", "Target organization for contact import");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setMaxFileSize(MAX_FILE_SIZE);
        upload.setAcceptedFileTypes("text/csv", ".csv", "text/xml", "application/xml", ".xml");
        upload.getElement().setAttribute("aria-label", "Contacts CSV upload");
        upload.addSucceededListener(event -> {
            try {
                contactsFileName = event.getFileName();
                contactsCsv = buffer.getInputStream().readAllBytes();
                runContactImport.setEnabled(true);
            } catch (IOException e) {
                Ui.error("Could not read the uploaded file: " + e.getMessage());
            }
        });
        upload.addFileRejectedListener(event -> Ui.error("File rejected: " + event.getErrorMessage()));

        Checkbox dryRun = new Checkbox("Dry run (validate only)");

        runContactImport.setEnabled(false);
        runContactImport.getElement().setAttribute("aria-label", "Run contact import");
        runContactImport.addClickListener(event -> {
            if (contactsCsv == null || contactOrgSelect.getValue() == null) {
                Ui.error("Choose an organization and a CSV file first");
                return;
            }
            try {
                boolean partnerFeed = contactsFileName != null
                        && contactsFileName.toLowerCase().endsWith(".xml");
                ImportExportService.ImportResult result = partnerFeed
                        ? importExportService.importContactsXml(
                                contactOrgSelect.getValue().getId(), contactsCsv, dryRun.getValue())
                        : importExportService.importContacts(
                                contactOrgSelect.getValue().getId(), contactsCsv, dryRun.getValue());
                showImportResult(result, dryRun.getValue());
                contactsCsv = null;
                runContactImport.setEnabled(false);
            } catch (RuntimeException e) {
                fail(e);
            }
        });

        Anchor template = downloadAnchor("contacts-template.csv",
                () -> importExportService.contactsTemplate(), "Download template");

        Paragraph note = new Paragraph("CSV or a partner XML feed, up to 2 MB; every row is validated and problems "
                + "are reported per row. Nothing is written unless the whole file is clean.");
        note.addClassName("small");
        note.addClassName("muted");

        HorizontalLayout actions = new HorizontalLayout(runContactImport, template);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        card.add(contactOrgSelect, upload, dryRun, actions, note);
        return card;
    }

    private Div importOrganizationsCard() {
        Div card = Ui.card();
        Span title = new Span("Import organizations");
        title.addClassName("card-title");
        card.add(title);

        Paragraph explain = new Paragraph("Imported organizations are created in trial status; the importing "
                + "account manager automatically becomes their owner.");
        explain.addClassName("small");
        explain.addClassName("muted");
        card.add(explain);

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setMaxFileSize(MAX_FILE_SIZE);
        upload.setAcceptedFileTypes("text/csv", ".csv");
        upload.getElement().setAttribute("aria-label", "Organizations CSV upload");
        upload.addSucceededListener(event -> {
            try {
                orgsCsv = buffer.getInputStream().readAllBytes();
                runOrgImport.setEnabled(true);
            } catch (IOException e) {
                Ui.error("Could not read the uploaded file: " + e.getMessage());
            }
        });
        upload.addFileRejectedListener(event -> Ui.error("File rejected: " + event.getErrorMessage()));

        Checkbox dryRun = new Checkbox("Dry run (validate only)");

        runOrgImport.setEnabled(false);
        runOrgImport.getElement().setAttribute("aria-label", "Run organization import");
        runOrgImport.addClickListener(event -> {
            if (orgsCsv == null) {
                Ui.error("Choose a CSV file first");
                return;
            }
            try {
                ImportExportService.ImportResult result = importExportService.importOrganizations(
                        orgsCsv, dryRun.getValue());
                showImportResult(result, dryRun.getValue());
                orgsCsv = null;
                runOrgImport.setEnabled(false);
            } catch (RuntimeException e) {
                fail(e);
            }
        });

        Anchor template = downloadAnchor("organizations-template.csv",
                () -> importExportService.organizationsTemplate(), "Download template");

        HorizontalLayout actions = new HorizontalLayout(runOrgImport, template);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);

        card.add(upload, dryRun, actions);
        return card;
    }

    private Div exportsCard() {
        Div card = Ui.card();
        Span title = new Span("Export reports");
        title.addClassName("card-title");
        card.add(title);

        HorizontalLayout reports = new HorizontalLayout(
                downloadAnchor("renewals-at-risk.csv", () -> {
                    try {
                        return reportService.renewalsReport().content();
                    } catch (RuntimeException e) {
                        return errorCsv(e);
                    }
                }, "Renewals at risk (CSV)"),
                downloadAnchor("customer-health.csv", () -> {
                    try {
                        return reportService.healthReport().content();
                    } catch (RuntimeException e) {
                        return errorCsv(e);
                    }
                }, "Customer health (CSV)"),
                downloadAnchor("escalations.csv", () -> {
                    try {
                        return reportService.escalationReport().content();
                    } catch (RuntimeException e) {
                        return errorCsv(e);
                    }
                }, "Escalations (CSV)"),
                downloadAnchor("integrations.csv", () -> {
                    try {
                        return reportService.integrationsReport().content();
                    } catch (RuntimeException e) {
                        return errorCsv(e);
                    }
                }, "Integration endpoints (CSV)"));
        reports.setAlignItems(FlexComponent.Alignment.CENTER);

        Div divider = new Div();
        divider.addClassName("divider");

        exportOrgSelect.setLabel("Organization");
        exportOrgSelect.setItems(organizations);
        exportOrgSelect.setItemLabelGenerator(Organization::getName);
        exportOrgSelect.getElement().setAttribute("aria-label", "Organization for contact export");

        Button exportContacts = Ui.ghost("Export contacts (CSV)");
        exportContacts.getElement().setAttribute("aria-label", "Export contacts CSV for the selected organization");
        exportContacts.addClickListener(event -> exportContacts());

        HorizontalLayout orgExport = new HorizontalLayout(exportOrgSelect, exportContacts);
        orgExport.setAlignItems(FlexComponent.Alignment.END);

        card.add(reports, divider, orgExport);
        return card;
    }

    private void exportContacts() {
        if (exportOrgSelect.getValue() == null) {
            Ui.error("Choose an organization first");
            return;
        }
        try {
            ReportService.Report report = reportService.contactsReport(exportOrgSelect.getValue().getId());
            StreamResource resource = new StreamResource(report.fileName(),
                    () -> new ByteArrayInputStream(report.content()));
            Anchor link = new Anchor(resource, "");
            link.getElement().setAttribute("download", true);
            anchorHost.removeAll();
            anchorHost.add(link);
            link.getElement().callJsFunction("click");
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void showImportResult(ImportExportService.ImportResult result, boolean dryRun) {
        if (result.ok()) {
            if (dryRun) {
                Ui.success("Validation passed — " + result.totalRows() + " rows ready to import");
            } else {
                Ui.success("Imported " + result.importedRows() + " rows");
            }
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Import finished with errors");
        dialog.setWidth("560px");

        Paragraph summary = new Paragraph("Imported " + result.importedRows() + " of " + result.totalRows()
                + " rows; the following problems were reported:");
        summary.addClassName("small");

        VerticalLayout errors = new VerticalLayout();
        errors.setPadding(false);
        errors.setSpacing(false);
        for (String message : result.errors()) {
            Paragraph row = new Paragraph(message);
            row.addClassName("small");
            row.getStyle().set("color", "#b42318");
            row.getStyle().set("margin", "2px 0");
            errors.add(row);
        }

        Button close = Ui.ghost("Close");
        close.addClickListener(event -> dialog.close());
        HorizontalLayout footer = new HorizontalLayout(close);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(summary, errors, footer);
        dialog.open();
    }

    private static Anchor downloadAnchor(String fileName, Supplier<byte[]> bytes, String label) {
        StreamResource resource = new StreamResource(fileName, () -> new ByteArrayInputStream(bytes.get()));
        Anchor anchor = new Anchor(resource, label);
        anchor.addClassName("ghost-button");
        anchor.getElement().setAttribute("download", true);
        return anchor;
    }

    private static byte[] errorCsv(RuntimeException e) {
        String message = e instanceof AccessDeniedException ? "Not permitted"
                : String.valueOf(e.getMessage());
        return ("\"error\",\"" + message.replace("\"", "'") + "\"\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void fail(RuntimeException e) {
        if (e instanceof AccessDeniedException) {
            Ui.error("Not permitted");
        } else if (e instanceof DecisionUnavailableException) {
            Ui.error("The authorization service is unavailable right now — please try again shortly.");
        } else {
            Ui.error(e.getMessage() == null ? "Something went wrong" : e.getMessage());
        }
    }
}
