package com.crmops.channel.ui;

import com.crmops.channel.domain.ApiKey;
import com.crmops.channel.domain.AssignmentRelation;
import com.crmops.channel.domain.AuditEvent;
import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.Deal;
import com.crmops.channel.domain.DealStage;
import com.crmops.channel.domain.DealType;
import com.crmops.channel.domain.DocCategory;
import com.crmops.channel.domain.DocVisibility;
import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.OrgStatus;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.Severity;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.ActivityService;
import com.crmops.channel.service.ApiKeyService;
import com.crmops.channel.service.ContactService;
import com.crmops.channel.service.CoreMockClient;
import com.crmops.channel.service.DealService;
import com.crmops.channel.service.DocumentService;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.InviteService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.ReferenceDataService;
import com.crmops.channel.service.UserService;
import com.crmops.channel.service.ValidationException;
import com.crmops.channel.service.WebhookService;
import com.crmops.channel.ui.components.Ui;
import com.crmops.channel.workflow.DealStageMachine;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Organization detail with role-aware tabs. Staff see contacts, deals,
 * escalations, documents, integrations and the full activity trail; customer
 * contacts see only their org's customer-visible surface (no health score, no
 * internal notes, no billing, no API keys). Every tab re-authorizes through
 * the decision tier when its data is loaded.
 */
@Route(value = "organizations/:orgId", layout = MainLayout.class)
@PageTitle("Organization · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT, Role.CUSTOMER_CONTACT})
public class OrganizationDetailView extends VerticalLayout implements HasUrlParameter<Long> {

    private final OrganizationService orgService;
    private final ContactService contactService;
    private final DealService dealService;
    private final EscalationService escalationService;
    private final DocumentService documentService;
    private final ApiKeyService apiKeyService;
    private final WebhookService webhookService;
    private final ActivityService activityService;
    private final UserService userService;
    private final CoreMockClient coreMockClient;
    private final InviteService inviteService;
    private final ReferenceDataService referenceDataService;

    private long orgId;
    private Organization org;
    private boolean staff;
    private boolean canManage;   // account manager or platform admin
    private boolean admin;
    private final Div tabContent = new Div();

    public OrganizationDetailView(OrganizationService orgService, ContactService contactService,
                                  DealService dealService, EscalationService escalationService,
                                  DocumentService documentService, ApiKeyService apiKeyService,
                                  WebhookService webhookService, ActivityService activityService,
                                  UserService userService, CoreMockClient coreMockClient,
                                  InviteService inviteService, ReferenceDataService referenceDataService) {
        this.orgService = orgService;
        this.contactService = contactService;
        this.dealService = dealService;
        this.escalationService = escalationService;
        this.documentService = documentService;
        this.apiKeyService = apiKeyService;
        this.webhookService = webhookService;
        this.activityService = activityService;
        this.userService = userService;
        this.coreMockClient = coreMockClient;
        this.inviteService = inviteService;
        this.referenceDataService = referenceDataService;
        addClassName("page");
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long parameter) {
        if (parameter == null) {
            event.rerouteTo(OrganizationListView.class);
            return;
        }
        try {
            org = orgService.get(parameter);
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
            event.rerouteTo(OrganizationListView.class);
            return;
        }
        orgId = org.getId();
        Role role = SecurityUtils.requireUser().role();
        staff = role != Role.CUSTOMER_CONTACT;
        canManage = role == Role.ACCOUNT_MANAGER || role == Role.PLATFORM_ADMIN;
        admin = role == Role.PLATFORM_ADMIN;
        buildShell();
    }

    private void buildShell() {
        removeAll();

        HorizontalLayout badges = new HorizontalLayout(
                Ui.planBadge(org.getPlan().getCode()),
                Ui.statusBadge(org.getStatus().name(), org.getStatus().label()),
                Ui.healthBadge(org.getHealthScore()));
        badges.setAlignItems(FlexComponent.Alignment.CENTER);
        badges.setSpacing(true);

        add(Ui.pageHeader(org.getName(),
                (org.getIndustry() == null ? "" : org.getIndustry() + " · ")
                        + (org.getCountry() == null ? "" : org.getCountry()),
                badges));

        Tabs tabs = new Tabs();
        tabs.addClassName("detail-tabs");
        Tab overviewTab = new Tab("Overview");
        Tab contactsTab = new Tab("Contacts");
        Tab dealsTab = new Tab("Deals");
        Tab escalationsTab = new Tab("Escalations");
        Tab documentsTab = new Tab("Documents");
        Tab integrationsTab = new Tab("Integrations");
        Tab activityTab = new Tab("Activity");
        if (canManage) {
            tabs.add(overviewTab, contactsTab, dealsTab, escalationsTab, documentsTab, integrationsTab, activityTab);
        } else if (staff) {
            // Support agents: no deals (billing-adjacent) and no integrations.
            tabs.add(overviewTab, contactsTab, escalationsTab, documentsTab, activityTab);
        } else {
            tabs.add(overviewTab, escalationsTab, documentsTab, contactsTab, activityTab);
        }
        tabs.addSelectedChangeListener(selection -> {
            Tab selected = selection.getSelectedTab();
            tabContent.removeAll();
            if (selected == overviewTab) {
                tabContent.add(buildOverview());
            } else if (selected == contactsTab) {
                tabContent.add(buildContacts());
            } else if (selected == dealsTab) {
                tabContent.add(buildDeals());
            } else if (selected == escalationsTab) {
                tabContent.add(buildEscalations());
            } else if (selected == documentsTab) {
                tabContent.add(buildDocuments());
            } else if (selected == integrationsTab) {
                tabContent.add(buildIntegrations());
            } else if (selected == activityTab) {
                tabContent.add(buildActivity());
            }
        });
        add(tabs, tabContent);
        tabContent.add(buildOverview());
    }

    /* ================= Overview ================= */

    private Div buildOverview() {
        Div wrapper = new Div();
        wrapper.addClassName("two-col");

        Div facts = Ui.card();
        H4 factsTitle = new H4("Account facts");
        factsTitle.addClassName("card-title");
        facts.add(factsTitle);
        facts.add(field("Plan", org.getPlan().getName() + " · " + Ui.money(org.getPlan().getBaseMonthlyPrice()) + "/mo"));
        facts.add(field("Status", org.getStatus().label()));
        facts.add(field("Health", org.getHealthScore() + "/100" + (canManage && org.getHealthRationale() != null
                ? " — " + org.getHealthRationale() : "")));
        facts.add(field("Renewal date", Ui.date(org.getRenewalDate())));
        facts.add(field("Seats", String.valueOf(org.getSeats()) + " (included: "
                + org.getPlan().getSeatsIncluded() + ")"));
        facts.add(field("Monthly revenue", Ui.money(OrganizationService.mrr(org))));
        facts.add(field("Employees", org.getEmployeeCount() == null ? "—" : String.valueOf(org.getEmployeeCount())));
        facts.add(field("Website", org.getWebsite() == null ? "—" : org.getWebsite()));
        facts.add(field("Created", Ui.date(org.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate())));
        wrapper.add(facts);

        Div actionsCard = Ui.card();
        H4 actionsTitle = new H4("Account actions");
        actionsTitle.addClassName("card-title");
        actionsCard.add(actionsTitle);
        if (canManage) {
            actionsCard.add(actionRow("Edit profile", "Name, industry, seats, website", VaadinIcon.EDIT,
                    this::openProfileDialog));
            actionsCard.add(actionRow("Update health score", "Score and rationale", VaadinIcon.HEART,
                    this::openHealthDialog));
            actionsCard.add(actionRow("Set renewal date", "Drives renewal-risk reporting", VaadinIcon.CALENDAR,
                    this::openRenewalDialog));
            if (admin) {
                actionsCard.add(actionRow("Change plan", "Pricing comes from the plan catalog", VaadinIcon.GRID,
                        this::openPlanDialog));
                actionsCard.add(actionRow("Change status", "Active / trial / suspended / churned", VaadinIcon.FLAG,
                        this::openStatusDialog));
            }
        } else if (staff) {
            actionsCard.add(new Paragraph("Support agents have read-only access to organizations."));
        } else {
            actionsCard.add(new Paragraph("As a customer contact you can manage your organization's contacts "
                    + "and documents from the tabs above."));
        }
        wrapper.add(actionsCard);

        if (canManage) {
            Div notesCard = Ui.card();
            H4 notesTitle = new H4("Internal notes");
            notesTitle.addClassName("card-title");
            notesCard.add(notesTitle);
            notesCard.add(new Paragraph("Internal only — never visible to customer contacts."));
            TextArea notesArea = new TextArea();
            notesArea.setWidthFull();
            notesArea.setPlaceholder("Renewal risk, stakeholder notes, internal context…");
            try {
                notesArea.setValue(orgService.internalNotes(orgId) == null ? "" : orgService.internalNotes(orgId));
            } catch (Exception ignored) {
                notesArea.setEnabled(false);
            }
            Button saveNotes = Ui.primary("Save notes");
            saveNotes.addClickListener(event -> {
                try {
                    orgService.updateInternalNotes(orgId, notesArea.getValue());
                    Ui.success("Notes saved");
                } catch (Exception e) {
                    Ui.error(OrganizationListView.messageOf(e));
                }
            });
            notesCard.add(notesArea, saveNotes);
            wrapper.add(notesCard);
        }

        if (canManage) {
            Div billingCard = Ui.card();
            H4 billingTitle = new H4("Billing (vendor core)");
            billingTitle.addClassName("card-title");
            billingCard.add(billingTitle);
            try {
                List<com.crmops.common.api.Dto.Invoice> invoices = coreMockClient.invoices(orgId);
                Grid<com.crmops.common.api.Dto.Invoice> invoiceGrid = new Grid<>(com.crmops.common.api.Dto.Invoice.class, false);
                invoiceGrid.addColumn(com.crmops.common.api.Dto.Invoice::number).setHeader("Invoice").setAutoWidth(true);
                invoiceGrid.addColumn(i -> Ui.money(i.amount())).setHeader("Amount").setAutoWidth(true);
                invoiceGrid.addColumn(i -> Ui.statusBadge(i.status(),
                        i.status().substring(0, 1) + i.status().substring(1).toLowerCase())).setHeader("Status").setAutoWidth(true);
                invoiceGrid.addColumn(i -> Ui.date(i.dueAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
                        .setHeader("Due").setAutoWidth(true);
                invoiceGrid.addColumn(i -> i.periodStart() + " → " + i.periodEnd()).setHeader("Period").setAutoWidth(true);
                invoiceGrid.setAllRowsVisible(true);
                invoiceGrid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.FILE,
                        "No invoices", "The vendor billing core has no invoices for this account."));
                billingCard.add(invoiceGrid);
            } catch (CoreMockClient.VendorCoreException e) {
                billingCard.add(Ui.emptyState(VaadinIcon.WARNING,
                        "Vendor billing core unavailable",
                        "Invoices are served by the mock vendor core — start it and reload."));
            }
            wrapper.add(billingCard);
        }
        return wrapper;
    }

    /* ================= Contacts ================= */

    private Div buildContacts() {
        Div wrapper = Ui.card();
        H4 title = new H4("Contacts");
        title.addClassName("card-title");
        wrapper.add(title);

        Button addContact = Ui.primary("Add contact");
        addContact.addClickListener(event -> openContactDialog(null));
        wrapper.add(addContact);

        Grid<Contact> grid = new Grid<>(Contact.class, false);
        grid.addColumn(Contact::fullName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(Contact::getEmail).setHeader("Email").setAutoWidth(true);
        grid.addColumn(contact -> contact.getTitle() == null ? "—" : contact.getTitle())
                .setHeader("Title").setAutoWidth(true);
        grid.addColumn(contact -> contact.getPhone() == null ? "—" : contact.getPhone())
                .setHeader("Phone").setAutoWidth(true);
        grid.addColumn(contact -> {
            HorizontalLayout chips = new HorizontalLayout();
            chips.setSpacing(false);
            if (contact.getPermissions() != null) {
                for (String permission : contact.getPermissions().split(",")) {
                    chips.add(new Span(permission.trim()) {{
                        addClassName("chip");
                    }});
                }
            }
            if (contact.isBilling()) {
                chips.add(new Span("BILLING") {{
                    addClassName("chip");
                }});
            }
            if (contact.isPrimary()) {
                chips.add(new Span("PRIMARY") {{
                    addClassName("chip");
                }});
            }
            return chips;
        }).setHeader("Roles").setAutoWidth(true);
        grid.addColumn(contact -> contact.getUserId() == null
                ? Ui.badge("No portal", "plain") : Ui.badge("Portal user", "success"))
                .setHeader("Portal").setAutoWidth(true);
        grid.addComponentColumn(contact -> {
            HorizontalLayout actions = new HorizontalLayout();
            Button edit = Ui.iconButton(VaadinIcon.EDIT, "Edit contact");
            edit.addClickListener(event -> openContactDialog(contact));
            actions.add(edit);
            if (staff && contact.getUserId() == null) {
                Button invite = Ui.ghost("Invite");
                invite.addClickListener(event -> inviteContact(contact));
                actions.add(invite);
            }
            return actions;
        }).setHeader("Actions").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.USERS, "No contacts yet",
                "Add the first contact for this organization."));
        try {
            grid.setItems(contactService.list(orgId));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        wrapper.add(grid);
        return wrapper;
    }

    private void inviteContact(Contact contact) {
        Ui.confirmDialog("Send portal invite", "Send an invite to " + contact.getEmail()
                + "? They'll set a password and gain portal access to this organization.", "Send invite", () -> {
            try {
                String link = inviteService.invite(orgId, contact.getId());
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Invitation created");
                dialog.add(new Paragraph("The invite email was written to the local outbox. Local demo link:"));
                Span linkSpan = new Span(link);
                linkSpan.addClassName("secret-once");
                dialog.add(linkSpan);
                Button close = Ui.ghost("Close");
                close.addClickListener(event -> dialog.close());
                dialog.add(close);
                dialog.open();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
    }

    private void openContactDialog(Contact existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add contact" : "Edit contact");
        dialog.setWidth("460px");
        TextField firstName = new TextField("First name");
        firstName.setWidthFull();
        TextField lastName = new TextField("Last name");
        lastName.setWidthFull();
        TextField email = new TextField("Email");
        email.setWidthFull();
        TextField phone = new TextField("Phone");
        phone.setWidthFull();
        TextField title = new TextField("Title");
        title.setWidthFull();
        TextField permissions = new TextField("Permissions (comma-separated)");
        permissions.setWidthFull();
        permissions.setPlaceholder("BILLING_CONTACT,TECHNICAL_CONTACT,PORTAL_ADMIN");
        Checkbox billing = new Checkbox("Billing contact");
        Checkbox primary = new Checkbox("Primary contact");
        if (existing != null) {
            firstName.setValue(existing.getFirstName());
            lastName.setValue(existing.getLastName());
            email.setValue(existing.getEmail());
            email.setReadOnly(true);
            phone.setValue(existing.getPhone() == null ? "" : existing.getPhone());
            title.setValue(existing.getTitle() == null ? "" : existing.getTitle());
            permissions.setValue(existing.getPermissions() == null ? "" : existing.getPermissions());
            billing.setValue(existing.isBilling());
            primary.setValue(existing.isPrimary());
        }
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            try {
                if (existing == null) {
                    contactService.create(orgId, firstName.getValue(), lastName.getValue(), email.getValue(),
                            phone.getValue(), title.getValue(), permissions.getValue(),
                            billing.getValue(), primary.getValue());
                } else {
                    contactService.update(existing.getId(), firstName.getValue(), lastName.getValue(),
                            phone.getValue(), title.getValue(), permissions.getValue(),
                            billing.getValue(), primary.getValue());
                }
                dialog.close();
                Ui.success("Contact saved");
                rebuildContactsTab();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(firstName, lastName, email, phone, title, permissions,
                billing, primary, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void rebuildContactsTab() {
        tabContent.removeAll();
        tabContent.add(buildContacts());
    }

    /* ================= Deals ================= */

    private Div buildDeals() {
        Div wrapper = Ui.card();
        H4 title = new H4("Deals & renewals");
        title.addClassName("card-title");
        wrapper.add(title);
        if (staff) {
            Button addDeal = Ui.primary("New deal");
            addDeal.addClickListener(event -> openDealDialog());
            wrapper.add(addDeal);
        }

        Grid<Deal> grid = new Grid<>(Deal.class, false);
        grid.addColumn(Deal::getName).setHeader("Deal").setAutoWidth(true);
        grid.addColumn(deal -> Ui.badge(deal.getDealType().label(), "neutral"))
                .setHeader("Type").setAutoWidth(true);
        grid.addColumn(deal -> Ui.dealStageBadge(deal.getStage().name()))
                .setHeader("Stage").setAutoWidth(true);
        grid.addColumn(deal -> Ui.money(deal.getAmount())).setHeader("Amount").setAutoWidth(true);
        grid.addColumn(deal -> deal.getProbability() + "%").setHeader("Probability").setAutoWidth(true);
        grid.addColumn(deal -> Ui.money(deal.expectedValue())).setHeader("Expected").setAutoWidth(true);
        grid.addColumn(deal -> Ui.date(deal.getCloseDate())).setHeader("Close date").setAutoWidth(true);
        if (staff) {
            grid.addComponentColumn(deal -> {
                HorizontalLayout actions = new HorizontalLayout();
                for (String action : DealStageMachine.allowedActions(deal.getStage()).stream().sorted().toList()) {
                    Button move = Ui.ghost(dealActionLabel(action));
                    move.addClickListener(event -> {
                        try {
                            dealService.transition(deal.getId(), action);
                            Ui.success("Deal moved to " + deal.getStage().label());
                            rebuildDealsTab();
                        } catch (Exception e) {
                            Ui.error(OrganizationListView.messageOf(e));
                        }
                    });
                    actions.add(move);
                }
                return actions;
            }).setHeader("Stage actions").setAutoWidth(true);
        }
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.CHART,
                "No deals yet", "Create the first deal for this account."));
        try {
            grid.setItems(dealService.list(orgId));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        wrapper.add(grid);
        return wrapper;
    }

    private String dealActionLabel(String action) {
        return switch (action) {
            case "qualify" -> "Qualify";
            case "propose" -> "Propose";
            case "negotiate" -> "Negotiate";
            case "win" -> "Won";
            case "lose" -> "Lost";
            case "mark_renewal_pending" -> "Renewal pending";
            case "renew" -> "Renewed";
            case "renegotiate" -> "Renegotiate";
            case "recycle" -> "Recycle";
            default -> action;
        };
    }

    private void rebuildDealsTab() {
        tabContent.removeAll();
        tabContent.add(buildDeals());
    }

    private void openDealDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New deal");
        dialog.setWidth("460px");
        TextField name = new TextField("Deal name");
        name.setWidthFull();
        Select<DealType> type = new Select<>();
        type.setLabel("Type");
        type.setItemLabelGenerator(DealType::label);
        type.setItems(DealType.values());
        type.setValue(DealType.NEW_BUSINESS);
        type.setWidthFull();
        TextField amount = new TextField("Amount (USD)");
        amount.setWidthFull();
        amount.setPattern("[0-9]*\\.?[0-9]*");
        IntegerField probability = new IntegerField("Probability (%)");
        probability.setWidthFull();
        probability.setMin(0);
        probability.setMax(100);
        probability.setValue(50);
        DatePicker closeDate = new DatePicker("Close date");
        closeDate.setWidthFull();
        TextArea notes = new TextArea("Notes");
        notes.setWidthFull();
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Create");
        save.addClickListener(event -> {
            try {
                BigDecimal value = new BigDecimal(amount.getValue().isBlank() ? "0" : amount.getValue());
                dealService.create(orgId, name.getValue(), type.getValue(), value,
                        probability.getValue(), closeDate.getValue(), notes.getValue());
                dialog.close();
                Ui.success("Deal created");
                rebuildDealsTab();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(name, type, amount, probability, closeDate, notes, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    /* ================= Escalations ================= */

    private Div buildEscalations() {
        Div wrapper = Ui.card();
        H4 title = new H4("Support escalations");
        title.addClassName("card-title");
        wrapper.add(title);
        Button addEscalation = Ui.primary(staff ? "Open escalation" : "New request");
        addEscalation.addClickListener(event -> openEscalationDialog());
        wrapper.add(addEscalation);

        Grid<EscalationService.EscalationView> grid = new Grid<>(EscalationService.EscalationView.class, false);
        grid.addColumn(EscalationService.EscalationView::subject).setHeader("Subject").setAutoWidth(true);
        grid.addColumn(view -> Ui.severityBadge(view.severity())).setHeader("Severity").setAutoWidth(true);
        grid.addColumn(view -> Ui.statusBadge(view.status(), statusLabel(view.status())))
                .setHeader("Status").setAutoWidth(true);
        if (staff) {
            grid.addColumn(view -> view.assigneeName() == null ? "Unassigned" : view.assigneeName())
                    .setHeader("Assignee").setAutoWidth(true);
        }
        grid.addColumn(view -> Ui.relative(view.updatedAt())).setHeader("Updated").setAutoWidth(true);
        grid.addItemClickListener(event -> UI.getCurrent().navigate("escalations/" + event.getItem().id()));
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.COMMENTS_O, "No escalations",
                "Escalations for this organization appear here."));
        try {
            grid.setItems(escalationService.listByOrg(orgId).stream()
                    .map(e -> escalationService.view(e.getId()))
                    .toList());
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        wrapper.add(grid);
        return wrapper;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "WAITING_ON_CUSTOMER" -> staff ? "Waiting on customer" : "Waiting on you";
            case "WAITING_ON_INTERNAL" -> "Waiting on internal";
            case "RESOLVED" -> "Resolved";
            case "CLOSED" -> "Closed";
            default -> "Open";
        };
    }

    private void openEscalationDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(staff ? "Open escalation" : "New support request");
        dialog.setWidth("480px");
        TextField subject = new TextField("Subject");
        subject.setWidthFull();
        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setPlaceholder("What happened, when, and what's the impact?");
        Select<Severity> severity = new Select<>();
        severity.setLabel("Severity");
        severity.setItemLabelGenerator(Severity::label);
        severity.setItems(Severity.values());
        severity.setValue(Severity.SEV3);
        severity.setWidthFull();
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Submit");
        save.addClickListener(event -> {
            try {
                Escalation created = escalationService.create(orgId, subject.getValue(),
                        description.getValue(), severity.getValue());
                dialog.close();
                Ui.success("Escalation opened");
                UI.getCurrent().navigate("escalations/" + created.getId());
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(subject, description, severity, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    /* ================= Documents ================= */

    private Div buildDocuments() {
        Div wrapper = Ui.card();
        H4 title = new H4("Documents");
        title.addClassName("card-title");
        wrapper.add(title);
        if (staff) {
            wrapper.add(buildUploadRow());
        }

        Grid<DocumentMeta> grid = new Grid<>(DocumentMeta.class, false);
        grid.addColumn(DocumentMeta::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(doc -> Ui.badge(doc.getCategory().label(), "neutral"))
                .setHeader("Category").setAutoWidth(true);
        grid.addColumn(doc -> Ui.visibilityBadge(doc.getVisibility().name()))
                .setHeader("Visibility").setAutoWidth(true);
        grid.addColumn(doc -> humanSize(doc.getSizeBytes())).setHeader("Size").setAutoWidth(true);
        grid.addColumn(doc -> Ui.date(doc.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate()))
                .setHeader("Uploaded").setAutoWidth(true);
        grid.addComponentColumn(doc -> {
            HorizontalLayout actions = new HorizontalLayout();
            actions.add(downloadAnchor(doc));
            if (staff && canManage) {
                Button edit = Ui.iconButton(VaadinIcon.EDIT, "Edit document");
                edit.addClickListener(event -> openDocumentEditDialog(doc));
                actions.add(edit);
                Button delete = Ui.iconButton(VaadinIcon.TRASH, "Delete document");
                delete.addClickListener(event -> Ui.confirmDialog("Delete document",
                        "Delete \"" + doc.getName() + "\"? This cannot be undone.", "Delete", () -> {
                            try {
                                documentService.delete(doc.getId());
                                Ui.success("Document deleted");
                                rebuildDocumentsTab();
                            } catch (Exception e) {
                                Ui.error(OrganizationListView.messageOf(e));
                            }
                        }));
                actions.add(delete);
                Button share = Ui.ghost("Share link");
                share.getElement().setAttribute("aria-label", "Create a share link for " + doc.getName());
                share.addClickListener(event -> {
                    try {
                        Ui.success(documentService.mintShareGrant(doc.getId(), null));
                    } catch (Exception e) {
                        Ui.error(OrganizationListView.messageOf(e));
                    }
                });
                actions.add(share);
            }
            return actions;
        }).setHeader("Actions").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.FILE, "No documents",
                staff ? "Upload the first document for this account."
                        : "No documents have been shared with you yet."));
        try {
            grid.setItems(documentService.list(orgId));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        wrapper.add(grid);
        return wrapper;
    }

    private Div buildUploadRow() {
        Div row = new Div();
        row.addClassName("divider");
        Select<DocCategory> category = new Select<>();
        category.setLabel("Category");
        category.setItemLabelGenerator(DocCategory::label);
        category.setItems(DocCategory.values());
        category.setValue(DocCategory.CONTRACT);
        category.setWidth("200px");
        Select<DocVisibility> visibility = new Select<>();
        visibility.setLabel("Visibility");
        visibility.setItemLabelGenerator(DocVisibility::label);
        visibility.setItems(DocVisibility.values());
        visibility.setValue(DocVisibility.INTERNAL);
        visibility.setWidth("180px");
        TextField description = new TextField("Description (optional)");
        description.setWidth("260px");
        var upload = Ui.inMemoryUpload(10 * 1024 * 1024,
                (metadata, data) -> {
                    try {
                        documentService.upload(orgId, new DocumentService.UploadCommand(
                                metadata.fileName(), category.getValue(), visibility.getValue(),
                                metadata.contentType(), metadata.contentLength(), description.getValue(), data));
                        Ui.success("Document uploaded");
                        rebuildDocumentsTab();
                    } catch (Exception e) {
                        Ui.error(OrganizationListView.messageOf(e));
                    }
                });
        Span hint = new Span("PDF, Word, Excel, CSV, TXT, PNG, JPG — max 10 MB");
        hint.addClassName("small");
        hint.addClassName("muted");
        HorizontalLayout controls = new HorizontalLayout(category, visibility, description);
        controls.setAlignItems(FlexComponent.Alignment.END);
        controls.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        TextField assetLink = new TextField("Or pull one in from a link");
        assetLink.setWidth("320px");
        assetLink.setPlaceholder("https://customer.example/brand/logo.png");
        Button pullIn = Ui.ghost("Import from link");
        pullIn.addClickListener(event -> {
            try {
                documentService.importFromLink(orgId, assetLink.getValue(), description.getValue());
                assetLink.clear();
                Ui.success("Document imported");
                rebuildDocumentsTab();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout linkRow = new HorizontalLayout(assetLink, pullIn);
        linkRow.setAlignItems(FlexComponent.Alignment.END);

        row.add(controls, hint, upload, linkRow);
        return row;
    }

    private Anchor downloadAnchor(DocumentMeta doc) {
        // The DownloadHandler supplier runs on EVERY download request, so
        // authorization is re-checked per request even for a re-used URL.
        return Ui.downloadAnchor("Download", "ghost-button", () -> {
            try {
                DocumentService.Download download = documentService.downloadStream(doc.getId());
                return new DownloadResponse(download.stream(), download.fileName(),
                        download.contentType(), download.sizeBytes());
            } catch (org.springframework.security.access.AccessDeniedException e) {
                return DownloadResponse.error(403, "Access denied");
            } catch (Exception e) {
                return DownloadResponse.error(404, "Not found");
            }
        });
    }

    private void openDocumentEditDialog(DocumentMeta doc) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit document");
        dialog.setWidth("440px");
        TextField name = new TextField("File name");
        name.setWidthFull();
        name.setValue(doc.getName());
        Select<DocCategory> category = new Select<>();
        category.setLabel("Category");
        category.setItemLabelGenerator(DocCategory::label);
        category.setItems(DocCategory.values());
        category.setValue(doc.getCategory());
        category.setWidthFull();
        Select<DocVisibility> visibility = new Select<>();
        visibility.setLabel("Visibility");
        visibility.setItemLabelGenerator(DocVisibility::label);
        visibility.setItems(DocVisibility.values());
        visibility.setValue(doc.getVisibility());
        visibility.setWidthFull();
        TextField description = new TextField("Description");
        description.setWidthFull();
        description.setValue(doc.getDescription() == null ? "" : doc.getDescription());
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            try {
                documentService.updateMeta(doc.getId(), name.getValue(), category.getValue(),
                        visibility.getValue(), description.getValue());
                dialog.close();
                Ui.success("Document updated");
                rebuildDocumentsTab();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(name, category, visibility, description, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void rebuildDocumentsTab() {
        tabContent.removeAll();
        tabContent.add(buildDocuments());
    }

    /* ================= Integrations (staff, billing-gated) ================= */

    private Div buildIntegrations() {
        Div wrapper = new Div();
        wrapper.addClassName("two-col");
        wrapper.add(buildApiKeysCard());
        wrapper.add(buildWebhooksCard());
        return wrapper;
    }

    private Div buildApiKeysCard() {
        Div card = Ui.card();
        H4 title = new H4("API keys");
        title.addClassName("card-title");
        card.add(title);
        if (admin) {
            Button create = Ui.primary("Create key");
            create.addClickListener(event -> openApiKeyCreateDialog());
            card.add(create);
        } else {
            card.add(new Paragraph("Platform admins rotate and revoke keys. You can see key metadata."));
        }
        Grid<ApiKey> grid = new Grid<>(ApiKey.class, false);
        grid.addColumn(ApiKey::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(key -> {
            Span masked = new Span(key.maskedValue());
            masked.addClassName("mono");
            return masked;
        }).setHeader("Key").setAutoWidth(true);
        grid.addColumn(key -> Ui.statusBadge(key.getStatus().name(), key.getStatus().label()))
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(key -> key.getExpiresAt() == null ? "Never" : Ui.dateTime(key.getExpiresAt()))
                .setHeader("Expires").setAutoWidth(true);
        grid.addColumn(key -> key.getLastUsedAt() == null ? "Never" : Ui.relative(key.getLastUsedAt()))
                .setHeader("Last used").setAutoWidth(true);
        if (admin) {
            grid.addComponentColumn(key -> {
                HorizontalLayout actions = new HorizontalLayout();
                if (key.getStatus() == com.crmops.channel.domain.ApiKeyStatus.ACTIVE) {
                    Button rotate = Ui.ghost("Rotate");
                    rotate.addClickListener(event -> Ui.confirmDialog("Rotate API key",
                            "A new key will be created and \"" + key.getName()
                                    + "\" will be revoked immediately.", "Rotate", () -> {
                                try {
                                    ApiKeyService.CreatedKey created = apiKeyService.rotate(key.getId());
                                    showKeyOnce(created);
                                    rebuildIntegrationsTab();
                                } catch (Exception e) {
                                    Ui.error(OrganizationListView.messageOf(e));
                                }
                            }));
                    actions.add(rotate);
                    Button revoke = Ui.ghost("Revoke");
                    revoke.addClassName("danger-button");
                    revoke.addClickListener(event -> Ui.confirmDialog("Revoke API key",
                            "Revoke \"" + key.getName() + "\"? Integrations using it will stop working.",
                            "Revoke", () -> {
                                try {
                                    apiKeyService.revoke(key.getId());
                                    Ui.success("Key revoked");
                                    rebuildIntegrationsTab();
                                } catch (Exception e) {
                                    Ui.error(OrganizationListView.messageOf(e));
                                }
                            }));
                    actions.add(revoke);
                }
                return actions;
            }).setHeader("Actions").setAutoWidth(true);
        }
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.KEY, "No API keys",
                "Create the first key for this organization."));
        try {
            grid.setItems(apiKeyService.list(orgId));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        card.add(grid);
        return card;
    }

    private void openApiKeyCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Create API key");
        dialog.setWidth("440px");
        TextField name = new TextField("Key name");
        name.setWidthFull();
        name.setPlaceholder("e.g. Production integration");
        DatePicker expiry = new DatePicker("Expires (optional)");
        expiry.setWidthFull();
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Create");
        save.addClickListener(event -> {
            try {
                Instant expiresAt = expiry.getValue() == null ? null
                        : expiry.getValue().atTime(23, 59).atZone(java.time.ZoneId.systemDefault()).toInstant();
                ApiKeyService.CreatedKey created = apiKeyService.create(orgId, name.getValue(), expiresAt);
                dialog.close();
                showKeyOnce(created);
                rebuildIntegrationsTab();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(name, expiry, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void showKeyOnce(ApiKeyService.CreatedKey created) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("API key created — shown once");
        dialog.add(new Paragraph("Copy this key now; it will never be displayed again. Only its hash is stored."));
        Span secret = new Span(created.plaintextSecret());
        secret.addClassName("secret-once");
        dialog.add(secret);
        Button close = Ui.primary("I've copied it");
        close.addClickListener(event -> dialog.close());
        dialog.add(close);
        dialog.open();
    }

    private Div buildWebhooksCard() {
        Div card = Ui.card();
        H4 title = new H4("Webhooks");
        title.addClassName("card-title");
        card.add(title);
        if (canManage) {
            Button create = Ui.primary("Add webhook");
            create.addClickListener(event -> openWebhookDialog(null));
            card.add(create);
        }
        Grid<com.crmops.channel.domain.WebhookConfig> grid = new Grid<>(com.crmops.channel.domain.WebhookConfig.class, false);
        grid.addColumn(com.crmops.channel.domain.WebhookConfig::getUrl).setHeader("URL").setAutoWidth(true);
        grid.addColumn(config -> {
            HorizontalLayout chips = new HorizontalLayout();
            chips.setSpacing(false);
            for (String event : config.getEvents().split(",")) {
                chips.add(new Span(event) {{
                    addClassName("chip");
                }});
            }
            return chips;
        }).setHeader("Events").setAutoWidth(true);
        grid.addColumn(config -> Ui.statusBadge(config.getStatus().name(), config.getStatus().label()))
                .setHeader("Status").setAutoWidth(true);
        if (canManage) {
            grid.addComponentColumn(config -> {
                HorizontalLayout actions = new HorizontalLayout();
                Button test = Ui.ghost("Test delivery");
                test.addClickListener(event -> testWebhook(config));
                actions.add(test);
                Button history = Ui.ghost("History");
                history.addClickListener(event -> showDeliveryHistory(config));
                actions.add(history);
                Button edit = Ui.iconButton(VaadinIcon.EDIT, "Edit webhook");
                edit.addClickListener(event -> openWebhookDialog(config));
                actions.add(edit);
                Button delete = Ui.iconButton(VaadinIcon.TRASH, "Delete webhook");
                delete.addClickListener(event -> Ui.confirmDialog("Delete webhook",
                        "Delete the webhook for " + config.getUrl() + "?", "Delete", () -> {
                            try {
                                webhookService.deleteConfig(config.getId());
                                Ui.success("Webhook deleted");
                                rebuildIntegrationsTab();
                            } catch (Exception e) {
                                Ui.error(OrganizationListView.messageOf(e));
                            }
                        }));
                actions.add(delete);
                return actions;
            }).setHeader("Actions").setAutoWidth(true);
        }
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.CONNECT,
                "No webhooks", "Configure a webhook to receive workflow events."));
        try {
            grid.setItems(webhookService.listConfigs(orgId));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        card.add(grid);
        return card;
    }

    private void testWebhook(com.crmops.channel.domain.WebhookConfig config) {
        Select<String> eventSelect = new Select<>();
        eventSelect.setLabel("Event to test");
        eventSelect.setItems(WebhookService.KNOWN_EVENTS);
        eventSelect.setWidthFull();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Test delivery");
        VerticalLayout content = new VerticalLayout(eventSelect);
        Button send = Ui.primary("Send test event");
        send.addClickListener(event -> {
            try {
                var delivery = webhookService.testDelivery(config.getId(), eventSelect.getValue());
                dialog.close();
                if (delivery.getStatus() == com.crmops.channel.domain.DeliveryStatus.SUCCESS) {
                    Ui.success("Delivered: HTTP " + delivery.getResponseStatus() + " — "
                            + truncate(delivery.getResponseSummary(), 120));
                } else {
                    Ui.error("Delivery failed: " + (delivery.getErrorMessage() == null
                            ? "unknown error" : delivery.getErrorMessage()));
                }
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        content.add(send);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void showDeliveryHistory(com.crmops.channel.domain.WebhookConfig config) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delivery history");
        dialog.setWidth("720px");
        Grid<com.crmops.channel.domain.WebhookDelivery> grid =
                new Grid<>(com.crmops.channel.domain.WebhookDelivery.class, false);
        grid.addColumn(d -> Ui.dateTime(d.getCreatedAt())).setHeader("Time").setAutoWidth(true);
        grid.addColumn(com.crmops.channel.domain.WebhookDelivery::getEventType)
                .setHeader("Event").setAutoWidth(true);
        grid.addColumn(d -> Ui.statusBadge(d.getStatus().name(), d.getStatus().label()))
                .setHeader("Result").setAutoWidth(true);
        grid.addColumn(d -> d.getResponseStatus() == null ? "—" : String.valueOf(d.getResponseStatus()))
                .setHeader("HTTP").setAutoWidth(true);
        grid.addColumn(d -> d.getResponseSummary() == null
                ? (d.getErrorMessage() == null ? "—" : d.getErrorMessage())
                : truncate(d.getResponseSummary(), 160)).setHeader("Response").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.CONNECT, "No deliveries yet",
                "Test deliveries and workflow events are recorded here."));
        try {
            grid.setItems(webhookService.deliveryHistory(config.getId()));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        Button close = Ui.ghost("Close");
        close.addClickListener(event -> dialog.close());
        dialog.add(grid, close);
        dialog.open();
    }

    private void openWebhookDialog(com.crmops.channel.domain.WebhookConfig existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add webhook" : "Edit webhook");
        dialog.setWidth("500px");
        TextField url = new TextField("Endpoint URL");
        url.setWidthFull();
        url.setPlaceholder("https://… or http://localhost:8082/mock/webhook-sink/your-org (local demo)");
        TextField description = new TextField("Description");
        description.setWidthFull();
        Span probeResult = new Span();
        probeResult.addClassName("small");
        probeResult.addClassName("muted");
        Button check = Ui.ghost("Check endpoint");
        check.addClickListener(event -> {
            try {
                probeResult.setText(webhookService.probeEndpoint(url.getValue()));
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        VerticalLayout eventChecks = new VerticalLayout();
        eventChecks.setPadding(false);
        eventChecks.setSpacing(false);
        Map<String, Checkbox> checkboxes = new java.util.LinkedHashMap<>();
        for (String event : WebhookService.KNOWN_EVENTS.stream().sorted().toList()) {
            Checkbox checkbox = new Checkbox(event);
            checkboxes.put(event, checkbox);
            eventChecks.add(checkbox);
        }
        if (existing != null) {
            url.setValue(existing.getUrl());
            description.setValue(existing.getDescription() == null ? "" : existing.getDescription());
            for (String event : existing.getEvents().split(",")) {
                Checkbox checkbox = checkboxes.get(event.trim());
                if (checkbox != null) {
                    checkbox.setValue(true);
                }
            }
        }
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            List<String> events = checkboxes.entrySet().stream()
                    .filter(entry -> entry.getValue().getValue())
                    .map(Map.Entry::getKey)
                    .toList();
            try {
                if (existing == null) {
                    webhookService.createConfig(orgId, url.getValue(), description.getValue(), events);
                } else {
                    webhookService.updateConfig(existing.getId(), url.getValue(), description.getValue(),
                            events, existing.getStatus() == com.crmops.channel.domain.WebhookConfigStatus.ACTIVE);
                }
                dialog.close();
                Ui.success("Webhook saved");
                rebuildIntegrationsTab();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(url, description, check, probeResult,
                new Paragraph("Subscribed events:"), eventChecks, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void rebuildIntegrationsTab() {
        tabContent.removeAll();
        tabContent.add(buildIntegrations());
    }

    /* ================= Activity ================= */

    private Div buildActivity() {
        Div card = Ui.card();
        H4 title = new H4("Activity");
        title.addClassName("card-title");
        card.add(title);
        if (!staff) {
            card.add(new Paragraph("Only customer-visible events are shown in the portal."));
        }
        try {
            List<AuditEvent> events = activityService.orgTimeline(orgId);
            if (events.isEmpty()) {
                card.add(Ui.emptyState(VaadinIcon.CLIPBOARD_TEXT, "No activity yet",
                        "Workflow actions will show up here."));
            }
            for (AuditEvent event : events) {
                Div row = new Div();
                row.addClassName("list-row");
                Span action = new Span(event.getAction());
                action.addClassName("mono");
                action.addClassName("small");
                Span meta = new Span((event.getActorUsername() == null ? "system" : event.getActorUsername())
                        + " · " + Ui.relative(event.getOccurredAt())
                        + (staff ? " · basis: " + event.getAuthorizationBasis() : ""));
                meta.addClassName("small");
                meta.addClassName("muted");
                row.add(action, meta);
                card.add(row);
            }
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
        return card;
    }

    /* ================= dialogs: profile / health / renewal / plan / status ================= */

    private void openProfileDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit profile");
        dialog.setWidth("460px");
        TextField name = new TextField("Name");
        name.setWidthFull();
        name.setValue(org.getName());
        TextField industry = new TextField("Industry");
        industry.setWidthFull();
        industry.setValue(org.getIndustry() == null ? "" : org.getIndustry());
        TextField country = new TextField("Country");
        country.setWidthFull();
        country.setValue(org.getCountry() == null ? "" : org.getCountry());
        IntegerField employees = new IntegerField("Employees");
        employees.setWidthFull();
        employees.setValue(org.getEmployeeCount());
        IntegerField seats = new IntegerField("Seats");
        seats.setWidthFull();
        seats.setValue(org.getSeats());
        TextField website = new TextField("Website");
        website.setWidthFull();
        website.setValue(org.getWebsite() == null ? "" : org.getWebsite());
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            try {
                org = orgService.updateProfile(orgId, name.getValue(), industry.getValue(),
                        country.getValue(), employees.getValue(), seats.getValue(), website.getValue());
                dialog.close();
                Ui.success("Profile updated");
                rebuildOverview();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(name, industry, country, employees, seats, website, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void openHealthDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Update health score");
        dialog.setWidth("440px");
        IntegerField score = new IntegerField("Score (0-100)");
        score.setWidthFull();
        score.setMin(0);
        score.setMax(100);
        score.setValue(org.getHealthScore());
        TextArea rationale = new TextArea("Rationale");
        rationale.setWidthFull();
        rationale.setValue(org.getHealthRationale() == null ? "" : org.getHealthRationale());
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            try {
                org = orgService.updateHealth(orgId, score.getValue(), rationale.getValue());
                dialog.close();
                Ui.success("Health updated");
                rebuildOverview();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(score, rationale, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void openRenewalDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Set renewal date");
        DatePicker renewal = new DatePicker("Renewal date");
        renewal.setValue(org.getRenewalDate());
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            try {
                org = orgService.updateRenewal(orgId, renewal.getValue());
                dialog.close();
                Ui.success("Renewal date updated");
                rebuildOverview();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(renewal, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void openPlanDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Change plan");
        Select<com.crmops.channel.domain.Plan> planSelect = new Select<>();
        planSelect.setLabel("Plan");
        planSelect.setItemLabelGenerator(p -> p.getName() + " · " + Ui.money(p.getBaseMonthlyPrice()) + "/mo");
        planSelect.setItems(referenceDataService.activePlans());
        planSelect.setValue(org.getPlan());
        planSelect.setWidthFull();
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Change plan");
        save.addClickListener(event -> {
            try {
                org = orgService.changePlan(orgId, planSelect.getValue().getId());
                dialog.close();
                Ui.success("Plan changed");
                rebuildOverview();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(planSelect, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void openStatusDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Change status");
        Select<OrgStatus> statusSelect = new Select<>();
        statusSelect.setLabel("Status");
        statusSelect.setItemLabelGenerator(OrgStatus::label);
        statusSelect.setItems(OrgStatus.values());
        statusSelect.setValue(org.getStatus());
        statusSelect.setWidthFull();
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Change status");
        save.addClickListener(event -> {
            try {
                org = orgService.changeStatus(orgId, statusSelect.getValue());
                dialog.close();
                Ui.success("Status changed");
                rebuildOverview();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(statusSelect, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void rebuildOverview() {
        tabContent.removeAll();
        tabContent.add(buildOverview());
    }

    /* ================= helpers ================= */

    private Div field(String label, String value) {
        Div div = new Div();
        Paragraph l = new Paragraph(label);
        l.addClassName("detail-field-label");
        Paragraph v = new Paragraph(value);
        v.addClassName("detail-field-value");
        div.add(l, v);
        return div;
    }

    private Div actionRow(String label, String hint, VaadinIcon icon, Runnable action) {
        Div row = new Div();
        row.addClassName("list-row");
        row.addClassName("clickable");
        row.add(icon.create());
        Span title = new Span(label);
        title.addClassName("list-row-title");
        Span hintSpan = new Span(hint);
        hintSpan.addClassName("small");
        hintSpan.addClassName("muted");
        row.add(title, hintSpan);
        row.addClickListener(event -> action.run());
        return row;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "—";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
