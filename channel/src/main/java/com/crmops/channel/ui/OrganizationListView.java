package com.crmops.channel.ui;

import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.OrganizationQueryService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.ReferenceDataService;
import com.crmops.channel.service.UserService;
import com.crmops.channel.service.ValidationException;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Organization directory with search and filters, scoped by the decision tier. */
@Route(value = "organizations", layout = MainLayout.class)
@PageTitle("Organizations · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT})
public class OrganizationListView extends VerticalLayout {

    private final OrganizationService orgService;
    private final UserService userService;
    private final ReferenceDataService referenceDataService;
    private final Grid<Organization> grid = new Grid<>(Organization.class, false);

    private TextField searchField;
    private Select<String> planFilter;
    private Select<String> statusFilter;
    private Select<String> healthFilter;
    private Select<String> sortSelect;

    public OrganizationListView(OrganizationService orgService,
                                UserService userService, ReferenceDataService referenceDataService) {
        this.orgService = orgService;
        this.userService = userService;
        this.referenceDataService = referenceDataService;
        addClassName("page");

        boolean canCreate = SecurityUtils.current().role() != Role.SUPPORT_AGENT;
        Button newOrg = Ui.primary("New organization");
        newOrg.addClickListener(event -> openCreateDialog());
        newOrg.setVisible(canCreate);
        add(Ui.pageHeader("Organizations", "Customer accounts visible to your role.", newOrg));

        add(buildFilterBar());
        add(buildGrid());
        refresh();
    }

    private Div buildFilterBar() {
        Div bar = new Div();
        bar.addClassName("filter-bar");
        searchField = new TextField();
        searchField.setPlaceholder("Search name, industry, country…");
        searchField.setWidth("260px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.setClearButtonVisible(true);

        planFilter = new Select<>();
        planFilter.setItems("All plans", "STARTER", "PRO", "ENTERPRISE");
        planFilter.setValue("All plans");
        planFilter.setWidth("150px");

        statusFilter = new Select<>();
        statusFilter.setItems("All statuses", "ACTIVE", "TRIAL", "SUSPENDED", "CHURNED");
        statusFilter.setValue("All statuses");
        statusFilter.setWidth("150px");

        healthFilter = new Select<>();
        healthFilter.setItems("All health", "EXCELLENT", "GOOD", "AT_RISK", "CRITICAL");
        healthFilter.setValue("All health");
        healthFilter.setWidth("140px");

        sortSelect = new Select<>();
        sortSelect.setItems("Name", "Renewal date", "Health score", "Newest");
        sortSelect.setValue("Name");
        sortSelect.setWidth("150px");

        Button apply = Ui.primary("Apply");
        apply.addClickListener(event -> refresh());
        Button reset = Ui.ghost("Reset");
        reset.addClickListener(event -> {
            searchField.clear();
            planFilter.setValue("All plans");
            statusFilter.setValue("All statuses");
            healthFilter.setValue("All health");
            sortSelect.setValue("Name");
            refresh();
        });
        bar.add(searchField, planFilter, statusFilter, healthFilter, sortSelect, apply, reset);
        return bar;
    }

    private Div buildGrid() {
        Div wrapper = new Div();
        wrapper.addClassName("grid-card");

        grid.addColumn(org -> {
            Div cell = new Div();
            Span name = new Span(org.getName());
            name.addClassName("list-row-title");
            Span industry = new Span(org.getIndustry() == null ? "—" : org.getIndustry());
            industry.addClassName("small");
            industry.addClassName("muted");
            cell.add(name, industry);
            return cell;
        }).setHeader("Organization").setAutoWidth(true);

        grid.addColumn(org -> Ui.planBadge(org.getPlan().getCode()))
                .setHeader("Plan").setAutoWidth(true);
        grid.addColumn(org -> Ui.statusBadge(org.getStatus().name(), org.getStatus().label()))
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(org -> Ui.healthBadge(org.getHealthScore()))
                .setHeader("Health").setAutoWidth(true);
        grid.addColumn(org -> ownerName(org.getOwnerUserId()))
                .setHeader("Owner").setAutoWidth(true);
        grid.addColumn(org -> Ui.date(org.getRenewalDate()))
                .setHeader("Renewal").setAutoWidth(true);
        grid.addColumn(org -> Ui.money(OrganizationService.mrr(org)))
                .setHeader("MRR").setAutoWidth(true);

        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.BUILDING,
                "No organizations match", "Adjust the filters or create a new organization."));
        grid.addItemClickListener(event -> UI.getCurrent().navigate("organizations/" + event.getItem().getId()));
        wrapper.add(grid);
        return wrapper;
    }

    private void refresh() {
        String plan = "All plans".equals(planFilter.getValue()) ? null : planFilter.getValue();
        String status = "All statuses".equals(statusFilter.getValue()) ? null : statusFilter.getValue();
        String health = "All health".equals(healthFilter.getValue()) ? null : healthFilter.getValue();
        String sort = switch (sortSelect.getValue()) {
            case "Renewal date" -> "renewalDate";
            case "Health score" -> "healthScore";
            case "Newest" -> "createdAt";
            default -> "name";
        };
        try {
            List<Organization> orgs = orgService.search(new OrganizationQueryService.SearchCriteria(
                    null, searchField.getValue(), plan, status, health, sort, "asc"));
            grid.setItems(orgs);
            ownerNames.clear();
            orgs.forEach(org -> ownerNames.put(org.getId(), org.getOwnerUserId()));
            Map<Long, String> names = userService.displayNames(ownerNames.values().stream()
                    .filter(java.util.Objects::nonNull).toList());
            nameById.clear();
            nameById.putAll(names);
            grid.getDataProvider().refreshAll();
        } catch (Exception e) {
            Ui.error(messageOf(e));
        }
    }

    private final Map<Long, Long> ownerNames = new HashMap<>();
    private final Map<Long, String> nameById = new HashMap<>();

    private String ownerName(Long ownerUserId) {
        if (ownerUserId == null) {
            return "Unassigned";
        }
        return nameById.getOrDefault(ownerUserId, "—");
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New organization");
        dialog.setWidth("520px");

        TextField name = new TextField("Name");
        name.setWidthFull();
        Select<com.crmops.channel.domain.Plan> planSelect = new Select<>();
        planSelect.setLabel("Plan");
        planSelect.setItemLabelGenerator(p -> p.getName() + " · " + Ui.money(p.getBaseMonthlyPrice()) + "/mo");
        planSelect.setItems(referenceDataService.activePlans());
        planSelect.setWidthFull();
        TextField industry = new TextField("Industry");
        industry.setWidthFull();
        TextField country = new TextField("Country");
        country.setWidthFull();
        IntegerField employees = new IntegerField("Employees");
        employees.setWidthFull();
        employees.setMin(1);
        IntegerField seats = new IntegerField("Seats");
        seats.setWidthFull();
        seats.setMin(1);
        seats.setValue(10);
        TextField website = new TextField("Website");
        website.setWidthFull();
        DatePicker renewal = new DatePicker("First renewal date");
        renewal.setWidthFull();
        TextArea notes = new TextArea("Internal notes");
        notes.setWidthFull();
        notes.setPlaceholder("Visible to staff only");

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Create");
        save.addClickListener(event -> {
            try {
                Organization org = orgService.create(name.getValue(),
                        planSelect.getValue() == null ? null : planSelect.getValue().getId(),
                        industry.getValue(), country.getValue(), employees.getValue(), seats.getValue(),
                        website.getValue(), renewal.getValue(), notes.getValue());
                dialog.close();
                Ui.success("Organization created");
                UI.getCurrent().navigate("organizations/" + org.getId());
            } catch (Exception e) {
                Ui.error(messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();

        VerticalLayout content = new VerticalLayout(name, planSelect, industry, country, employees, seats,
                website, renewal, notes, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    static String messageOf(Exception e) {
        if (e instanceof ValidationException) {
            return e.getMessage();
        }
        if (e instanceof org.springframework.security.access.AccessDeniedException) {
            return "You are not allowed to do that.";
        }
        return "Something went wrong: " + e.getMessage();
    }
}
