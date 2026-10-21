package com.crmops.channel.ui;

import com.crmops.channel.domain.EscalationStatus;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.Severity;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** The support team's escalation queue with severity, SLA age and assignment. */
@Route(value = "escalations", layout = MainLayout.class)
@PageTitle("Escalation queue · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT})
public class EscalationQueueView extends VerticalLayout {

    private final EscalationService escalationService;
    private final Grid<EscalationService.EscalationView> grid =
            new Grid<>(EscalationService.EscalationView.class, false);

    private TextField search;
    private Select<String> severity;
    private Select<String> status;
    private Select<String> assignee;
    private Checkbox breachedOnly;

    public EscalationQueueView(EscalationService escalationService) {
        this.escalationService = escalationService;
        addClassName("page");
        add(Ui.pageHeader("Escalation queue", "Every open escalation the team is working."));
        add(buildFilterBar());
        add(buildGrid());
        refresh();
    }

    private Div buildFilterBar() {
        Div bar = new Div();
        bar.addClassName("filter-bar");
        search = new TextField();
        search.setPlaceholder("Search subject…");
        search.setWidth("220px");
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setClearButtonVisible(true);

        severity = new Select<>();
        severity.setItems("All severities", "SEV1", "SEV2", "SEV3");
        severity.setValue("All severities");
        severity.setWidth("150px");

        status = new Select<>();
        status.setItems("Open items", "All statuses", "OPEN", "WAITING_ON_CUSTOMER", "WAITING_ON_INTERNAL",
                "RESOLVED", "CLOSED");
        status.setValue("Open items");
        status.setWidth("170px");

        assignee = new Select<>();
        assignee.setItems("Anyone", "Unassigned", "Assigned to me");
        assignee.setValue("Anyone");
        assignee.setWidth("150px");

        breachedOnly = new Checkbox("SLA breached only");

        Button apply = Ui.primary("Apply");
        apply.addClickListener(event -> refresh());
        Button reset = Ui.ghost("Reset");
        reset.addClickListener(event -> {
            search.clear();
            severity.setValue("All severities");
            status.setValue("Open items");
            assignee.setValue("Anyone");
            breachedOnly.setValue(false);
            refresh();
        });
        bar.add(search, severity, status, assignee, breachedOnly, apply, reset);
        return bar;
    }

    private Div buildGrid() {
        Div wrapper = new Div();
        wrapper.addClassName("grid-card");
        grid.addColumn(view -> Ui.severityBadge(view.severity()))
                .setHeader("Severity").setAutoWidth(true);
        grid.addColumn(view -> {
            Div cell = new Div();
            Span subject = new Span(view.subject());
            subject.addClassName("list-row-title");
            Span org = new Span(view.orgName());
            org.addClassName("small");
            org.addClassName("muted");
            cell.add(subject, org);
            return cell;
        }).setHeader("Escalation").setAutoWidth(true);
        grid.addColumn(view -> Ui.statusBadge(view.status(), statusLabel(view.status())))
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(view -> view.assigneeName() == null ? "Unassigned" : view.assigneeName())
                .setHeader("Assignee").setAutoWidth(true);
        grid.addColumn(view -> Ui.ageBadge(view.createdAt(), view.slaDueAt(), view.slaBreached()))
                .setHeader("Age / SLA").setAutoWidth(true);
        grid.addComponentColumn(view -> {
            HorizontalLayout actions = new HorizontalLayout();
            if (view.assigneeId() == null) {
                Button take = Ui.ghost("Assign to me");
                take.addClickListener(event -> {
                    try {
                        escalationService.assign(view.id(), SecurityUtils.requireUser().userId());
                        Ui.success("Assigned to you");
                        refresh();
                    } catch (Exception e) {
                        Ui.error(OrganizationListView.messageOf(e));
                    }
                });
                actions.add(take);
            }
            return actions;
        }).setHeader("Actions").setAutoWidth(true);
        grid.addItemClickListener(event -> UI.getCurrent().navigate("escalations/" + event.getItem().id()));
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.COMMENTS_O, "Queue is empty",
                "Nothing matches the current filters."));
        wrapper.add(grid);
        return wrapper;
    }

    private void refresh() {
        EscalationStatus statusFilter = statusFilter();
        Severity severityFilter = severityFilter();
        Long assigneeFilter = assigneeFilter();
        try {
            java.util.List<EscalationService.EscalationView> items;
            if (statusFilter == null && "All statuses".equals(status.getValue())) {
                items = new java.util.ArrayList<>();
                for (EscalationStatus s : EscalationStatus.values()) {
                    items.addAll(escalationService.queue(s, severityFilter, assigneeFilter, 200));
                }
            } else {
                items = escalationService.queue(statusFilter, severityFilter, assigneeFilter, 200);
            }
            String query = search.getValue() == null ? "" : search.getValue().trim().toLowerCase();
            if (!query.isBlank()) {
                items = items.stream().filter(v -> v.subject().toLowerCase().contains(query)).toList();
            }
            if (breachedOnly.getValue()) {
                items = items.stream().filter(EscalationService.EscalationView::slaBreached).toList();
            }
            grid.setItems(items);
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private EscalationStatus statusFilter() {
        return switch (status.getValue()) {
            case "OPEN" -> EscalationStatus.OPEN;
            case "WAITING_ON_CUSTOMER" -> EscalationStatus.WAITING_ON_CUSTOMER;
            case "WAITING_ON_INTERNAL" -> EscalationStatus.WAITING_ON_INTERNAL;
            case "RESOLVED" -> EscalationStatus.RESOLVED;
            case "CLOSED" -> EscalationStatus.CLOSED;
            default -> null; // "Open items" and "All statuses" handled below
        };
    }

    private Severity severityFilter() {
        return switch (severity.getValue()) {
            case "SEV1" -> Severity.SEV1;
            case "SEV2" -> Severity.SEV2;
            case "SEV3" -> Severity.SEV3;
            default -> null;
        };
    }

    private Long assigneeFilter() {
        return switch (assignee.getValue()) {
            case "Assigned to me" -> SecurityUtils.requireUser().userId();
            default -> null;
        };
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "WAITING_ON_CUSTOMER" -> "Waiting on customer";
            case "WAITING_ON_INTERNAL" -> "Waiting on internal";
            case "RESOLVED" -> "Resolved";
            case "CLOSED" -> "Closed";
            default -> "Open";
        };
    }
}
