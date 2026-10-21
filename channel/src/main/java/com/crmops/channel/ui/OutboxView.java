package com.crmops.channel.ui;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.domain.EmailOutbox;
import com.crmops.channel.domain.OutboxStatus;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.OutboxService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * Viewer over the local email outbox mock. Every outbound email lands here
 * instead of a real SMTP provider; the batch tier marks rows sent or failed.
 */
@Route("admin/outbox")
@RouteAccess({Role.PLATFORM_ADMIN})
@PageTitle("Email outbox · CrmOps")
public class OutboxView extends VerticalLayout {

    private final OutboxService outboxService;
    private final Div stats = new Div();
    private final Grid<EmailOutbox> grid = new Grid<>(EmailOutbox.class, false);

    public OutboxView(OutboxService outboxService) {
        this.outboxService = outboxService;
        addClassName("page");
        setPadding(false);
        setWidthFull();

        Button refresh = Ui.ghost("Refresh");
        refresh.getElement().setAttribute("aria-label", "Refresh the email outbox");
        refresh.addClickListener(event -> refresh());

        add(Ui.pageHeader("Email outbox", "Local mail queue — replaces a real SMTP provider for the demo.",
                refresh));

        stats.addClassName("stat-grid");
        add(stats);

        grid.addColumn(EmailOutbox::getToAddress).setHeader("To").setAutoWidth(true);
        grid.addColumn(EmailOutbox::getSubject).setHeader("Subject").setAutoWidth(true);
        grid.addColumn(email -> {
            Span chip = new Span(email.getTemplate());
            chip.addClassName("chip");
            return chip;
        }).setHeader("Template").setAutoWidth(true);
        grid.addColumn(email -> Ui.statusBadge(email.getStatus().name(), email.getStatus().label()))
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(email -> Ui.dateTime(email.getCreatedAt())).setHeader("Created").setAutoWidth(true);
        grid.addColumn(email -> Ui.dateTime(email.getSentAt())).setHeader("Sent").setAutoWidth(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.ENVELOPE, "No email yet",
                "Messages queued by the app will appear here."));
        grid.addItemClickListener(event -> openDetailDialog(event.getItem()));

        Div gridCard = new Div(grid);
        gridCard.addClassName("grid-card");
        add(gridCard);

        refresh();
    }

    private void refresh() {
        try {
            List<EmailOutbox> emails = outboxService.recent();
            long pending = emails.stream().filter(e -> e.getStatus() == OutboxStatus.PENDING).count();
            long sent = emails.stream().filter(e -> e.getStatus() == OutboxStatus.SENT).count();
            long failed = emails.stream().filter(e -> e.getStatus() == OutboxStatus.FAILED).count();
            stats.removeAll();
            stats.add(Ui.statCard("Pending", String.valueOf(pending), "Awaiting the batch tier", "warn"),
                    Ui.statCard("Sent", String.valueOf(sent), "Delivered by the batch tier", "success"),
                    Ui.statCard("Failed", String.valueOf(failed), "Delivery failed", "danger"));
            grid.setItems(emails);
            grid.setAllRowsVisible(true);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void openDetailDialog(EmailOutbox email) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(email.getSubject());
        dialog.setWidth("620px");

        HorizontalLayout meta = new HorizontalLayout(
                Ui.statusBadge(email.getStatus().name(), email.getStatus().label()),
                new Span("To " + email.getToAddress() + " · template " + email.getTemplate()));
        meta.setAlignItems(FlexComponent.Alignment.CENTER);

        Div body = new Div();
        body.addClassName("mono");
        body.addClassName("small");
        body.getStyle().set("white-space", "pre-wrap");
        body.getStyle().set("background", "#f7f9fb");
        body.getStyle().set("border-radius", "8px");
        body.getStyle().set("padding", "12px");
        body.getStyle().set("max-height", "300px");
        body.getStyle().set("overflow", "auto");
        body.setText(email.getBody());

        VerticalLayout content = new VerticalLayout(meta, body);
        content.setPadding(false);

        if (email.getErrorMessage() != null && !email.getErrorMessage().isBlank()) {
            Paragraph error = new Paragraph("Error: " + email.getErrorMessage());
            error.addClassName("small");
            error.getStyle().set("color", "#b42318");
            content.add(error);
        }

        Paragraph note = new Paragraph("This outbox replaces a real SMTP provider for the local demo.");
        note.addClassName("small");
        note.addClassName("muted");
        content.add(note);

        Button close = Ui.ghost("Close");
        close.addClickListener(event -> dialog.close());
        HorizontalLayout footer = new HorizontalLayout(close);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        dialog.add(content, footer);
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
