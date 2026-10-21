package com.crmops.channel.ui;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.domain.AuditEvent;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.AuditService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Read-only viewer over the append-only audit trail. There is deliberately no
 * edit or delete path here — no role can modify audit records.
 */
@Route("admin/audit")
@RouteAccess({Role.PLATFORM_ADMIN})
@PageTitle("Audit log · CrmOps")
public class AuditLogView extends VerticalLayout {

    private static final int PAGE_SIZE = 20;

    private final AuditService auditService;

    private final TextField actionFilter = new TextField("Action");
    private final TextField actorFilter = new TextField("Actor");
    private final NumberField orgFilter = new NumberField("Organization id");
    private final DateTimePicker fromFilter = new DateTimePicker("From");
    private final DateTimePicker toFilter = new DateTimePicker("To");
    private final Grid<AuditEvent> grid = new Grid<>(AuditEvent.class, false);
    private final Span pageLabel = new Span();
    private final Button prevButton = Ui.ghost("Prev");
    private final Button nextButton = Ui.ghost("Next");
    private int page;
    private int totalPages = 1;

    public AuditLogView(AuditService auditService) {
        this.auditService = auditService;
        addClassName("page");
        setPadding(false);
        setWidthFull();

        add(Ui.pageHeader("Audit log", "Append-only record of every privileged action."));

        Div banner = Ui.card(new Paragraph("This log is append-only — no role can modify or delete entries."));
        banner.addClassName("alert-banner");
        banner.addClassName("info");
        add(banner);

        Button apply = Ui.primary("Apply");
        apply.getElement().setAttribute("aria-label", "Apply audit filters");
        apply.addClickListener(event -> {
            page = 0;
            refresh();
        });
        Button reset = Ui.ghost("Reset");
        reset.getElement().setAttribute("aria-label", "Reset audit filters");
        reset.addClickListener(event -> {
            actionFilter.clear();
            actorFilter.clear();
            orgFilter.clear();
            fromFilter.clear();
            toFilter.clear();
            page = 0;
            refresh();
        });

        Div filterBar = new Div(actionFilter, actorFilter, orgFilter, fromFilter, toFilter, apply, reset);
        filterBar.addClassName("filter-bar");
        add(filterBar);

        grid.addColumn(event -> Ui.dateTime(event.getOccurredAt())).setHeader("Time").setAutoWidth(true);
        grid.addColumn(event -> {
            VerticalLayout cell = new VerticalLayout();
            cell.setPadding(false);
            cell.setSpacing(false);
            cell.add(new Span(event.getActorUsername() == null ? "system" : event.getActorUsername()));
            Span role = new Span(event.getActorRole() == null ? "SYSTEM" : event.getActorRole());
            role.addClassName("small");
            role.addClassName("muted");
            cell.add(role);
            return cell;
        }).setHeader("Actor").setAutoWidth(true);
        grid.addColumn(event -> {
            Span action = new Span(event.getAction());
            action.addClassName("mono");
            action.addClassName("small");
            return action;
        }).setHeader("Action").setAutoWidth(true);
        grid.addColumn(event -> (event.getTargetType() == null ? "—" : event.getTargetType())
                + (event.getTargetId() == null ? "" : ":" + event.getTargetId()))
                .setHeader("Target").setAutoWidth(true);
        grid.addColumn(event -> event.getOrgId() == null ? "—" : String.valueOf(event.getOrgId()))
                .setHeader("Org").setAutoWidth(true);
        grid.addColumn(event -> {
            Span basis = new Span(event.getAuthorizationBasis());
            basis.addClassName("small");
            basis.addClassName("muted");
            basis.getElement().setAttribute("title", event.getAuthorizationBasis());
            return basis;
        }).setHeader("Basis").setAutoWidth(true);
        grid.addColumn(event -> event.isCustomerVisible()
                        ? Ui.badge("Customer", "success") : Ui.badge("Internal", "accent"))
                .setHeader("Visible").setAutoWidth(true);
        grid.addColumn(event -> event.getIpAddress() == null ? "—" : event.getIpAddress())
                .setHeader("IP").setAutoWidth(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.CLIPBOARD_TEXT, "No audit events",
                "Nothing matches the current filters."));
        grid.addItemClickListener(event -> openDetailDialog(event.getItem()));

        Div gridCard = new Div(grid);
        gridCard.addClassName("grid-card");
        add(gridCard);

        prevButton.getElement().setAttribute("aria-label", "Previous page");
        prevButton.addClickListener(event -> {
            if (page > 0) {
                page--;
                refresh();
            }
        });
        nextButton.getElement().setAttribute("aria-label", "Next page");
        nextButton.addClickListener(event -> {
            if (page < totalPages - 1) {
                page++;
                refresh();
            }
        });
        pageLabel.addClassName("small");
        pageLabel.addClassName("muted");
        HorizontalLayout pager = new HorizontalLayout(prevButton, pageLabel, nextButton);
        pager.setAlignItems(FlexComponent.Alignment.CENTER);
        add(pager);

        refresh();
    }

    private void refresh() {
        Instant from = fromFilter.getValue() == null ? null
                : fromFilter.getValue().atZone(ZoneId.systemDefault()).toInstant();
        Instant to = toFilter.getValue() == null ? null
                : toFilter.getValue().atZone(ZoneId.systemDefault()).toInstant();
        try {
            Page<AuditEvent> result = auditService.search(
                    actionFilter.isEmpty() ? null : actionFilter.getValue().trim(),
                    actorFilter.isEmpty() ? null : actorFilter.getValue().trim(),
                    orgFilter.getValue() == null ? null : orgFilter.getValue().longValue(),
                    from, to, PageRequest.of(page, PAGE_SIZE, Sort.by("occurredAt").descending()));
            grid.setItems(result.getContent());
            grid.setAllRowsVisible(true);
            totalPages = Math.max(1, result.getTotalPages());
            pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
            prevButton.setEnabled(page > 0);
            nextButton.setEnabled(page < totalPages - 1);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void openDetailDialog(AuditEvent event) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(event.getAction() + " · " + Ui.dateTime(event.getOccurredAt()));
        dialog.setWidth("640px");

        TextArea before = new TextArea("Before");
        before.setValue(event.getBeforeJson() == null ? "" : event.getBeforeJson());
        before.setPlaceholder("(no before values recorded)");
        before.setReadOnly(true);
        before.addClassName("mono");
        before.setWidthFull();
        before.setHeight("150px");

        TextArea after = new TextArea("After");
        after.setValue(event.getAfterJson() == null ? "" : event.getAfterJson());
        after.setPlaceholder("(no after values recorded)");
        after.setReadOnly(true);
        after.addClassName("mono");
        after.setWidthFull();
        after.setHeight("150px");

        Paragraph meta = new Paragraph("Target " + (event.getTargetType() == null ? "—" : event.getTargetType())
                + (event.getTargetId() == null ? "" : " " + event.getTargetId())
                + (event.getOrgId() == null ? "" : " · org #" + event.getOrgId())
                + (event.getIpAddress() == null ? "" : " · ip " + event.getIpAddress()));
        meta.addClassName("small");
        meta.addClassName("muted");

        Paragraph basis = new Paragraph("Authorization basis: " + event.getAuthorizationBasis());
        basis.addClassName("small");
        basis.addClassName("muted");

        Button close = Ui.ghost("Close");
        close.addClickListener(e -> dialog.close());
        HorizontalLayout footer = new HorizontalLayout(close);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(before, after, meta, basis, footer);
        dialog.open();
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
