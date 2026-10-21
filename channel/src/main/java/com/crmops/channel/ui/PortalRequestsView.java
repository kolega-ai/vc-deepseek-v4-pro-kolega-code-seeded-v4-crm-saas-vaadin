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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Customer portal request list and composer. Requests land on the SAME
 * escalation workflow as staff-side ones; customers reach the shared
 * EscalationDetailView (customer-visible projection only).
 */
@Route(value = "portal/requests", layout = MainLayout.class)
@PageTitle("My requests · CrmOps")
@RouteAccess({Role.CUSTOMER_CONTACT})
public class PortalRequestsView extends VerticalLayout {

    private final EscalationService escalationService;
    private final Grid<EscalationService.EscalationView> grid =
            new Grid<>(EscalationService.EscalationView.class, false);

    public PortalRequestsView(EscalationService escalationService) {
        this.escalationService = escalationService;
        addClassName("page");

        Button newRequest = Ui.primary("New request");
        newRequest.addClickListener(event -> openCreateDialog());
        add(Ui.pageHeader("My requests", "Open, track and reply to support requests.", newRequest));

        add(buildGrid());
        refresh();
    }

    private Div buildGrid() {
        Div wrapper = new Div();
        wrapper.addClassName("grid-card");

        grid.addColumn(view -> {
            Div cell = new Div();
            Span subject = new Span(view.subject());
            subject.addClassName("list-row-title");
            cell.add(subject);
            return cell;
        }).setHeader("Subject").setAutoWidth(true);
        grid.addColumn(view -> Ui.severityBadge(view.severity()))
                .setHeader("Severity").setAutoWidth(true);
        grid.addColumn(view -> Ui.statusBadge(view.status(), statusLabel(view.status())))
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(view -> Ui.relative(view.updatedAt()))
                .setHeader("Updated").setAutoWidth(true);

        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.COMMENTS_O,
                "No requests yet", "Create your first support request."));
        grid.addItemClickListener(event -> UI.getCurrent().navigate("escalations/" + event.getItem().id()));
        wrapper.add(grid);
        return wrapper;
    }

    private void refresh() {
        try {
            grid.setItems(escalationService.queue(null, null, null, 100));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New request");
        dialog.setWidth("520px");

        TextField subject = new TextField("Subject");
        subject.setWidthFull();
        subject.setAutofocus(true);
        TextArea description = new TextArea("Description");
        description.setWidthFull();
        description.setPlaceholder("What is going wrong, and how can we help?");
        Select<Severity> severity = new Select<>();
        severity.setLabel("Severity");
        severity.setItemLabelGenerator(Severity::label);
        severity.setItems(Severity.values());
        severity.setValue(Severity.SEV3);
        severity.setWidthFull();

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Submit request");
        save.addClickListener(event -> {
            try {
                var escalation = escalationService.create(
                        SecurityUtils.requireUser().customerOrgId(),
                        subject.getValue(), description.getValue(), severity.getValue());
                dialog.close();
                Ui.success("Request submitted");
                refresh();
                UI.getCurrent().navigate("escalations/" + escalation.getId());
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

    private static String statusLabel(String status) {
        return switch (status) {
            case "WAITING_ON_CUSTOMER" -> "Waiting on you";
            case "WAITING_ON_INTERNAL" -> "Being worked on";
            default -> EscalationStatus.valueOf(status).label();
        };
    }
}
