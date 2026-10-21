package com.crmops.channel.ui;

import com.crmops.channel.domain.EscalationStatus;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;
import java.util.Map;

/** Support dashboard: queue stats, SLA pressure and the most urgent tickets. */
@Route(value = "support", layout = MainLayout.class)
@PageTitle("Support dashboard · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.SUPPORT_AGENT})
public class SupportDashboardView extends VerticalLayout {

    public SupportDashboardView(EscalationService escalationService) {
        addClassName("page");
        RouterLink openQueue = new RouterLink("Open the escalation queue", EscalationQueueView.class);
        openQueue.addClassName("primary-button");
        add(Ui.pageHeader("Support dashboard", "Team queue at a glance.", openQueue));

        Map<String, Long> stats = escalationService.slaStats();
        add(Ui.statGrid(
                Ui.statCard(String.valueOf(stats.get("open")), "Open escalations", "across the team", "primary"),
                Ui.statCard(String.valueOf(stats.get("breached")), "SLA breached", "response time overdue", "danger"),
                Ui.statCard(String.valueOf(stats.get("sev1")), "SEV1 · Critical", "handle first", "warn"),
                Ui.statCard(String.valueOf(stats.get("unassigned")), "Unassigned", "needs an owner", "info")));

        Div card = Ui.card();
        H4 title = new H4("Most urgent in the queue");
        title.addClassName("card-title");
        card.add(title);

        List<EscalationService.EscalationView> queue = escalationService.queue(
                EscalationStatus.OPEN, null, null, 10);
        if (queue.isEmpty()) {
            card.add(Ui.emptyState(com.vaadin.flow.component.icon.VaadinIcon.CHECK_CIRCLE,
                    "Queue is clear", "No open escalations right now."));
        } else {
            for (EscalationService.EscalationView view : queue) {
                Div row = new Div();
                row.addClassName("list-row");
                row.addClassName("clickable");
                row.add(Ui.severityBadge(view.severity()));
                Span subject = new Span(view.subject());
                subject.addClassName("list-row-title");
                Span meta = new Span(view.orgName() + " · "
                        + (view.assigneeName() == null ? "unassigned" : view.assigneeName()) + " · "
                        + Ui.relative(view.createdAt()));
                meta.addClassName("small");
                meta.addClassName("muted");
                row.add(subject, meta);
                row.add(Ui.statusBadge(view.status(),
                        switch (view.status()) {
                            case "WAITING_ON_CUSTOMER" -> "Waiting on customer";
                            case "WAITING_ON_INTERNAL" -> "Waiting on internal";
                            default -> "Open";
                        }));
                row.addClickListener(event -> UI.getCurrent().navigate("escalations/" + view.id()));
                card.add(row);
            }
        }
        add(card);
    }
}
