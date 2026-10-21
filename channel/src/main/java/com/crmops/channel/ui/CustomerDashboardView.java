package com.crmops.channel.ui;

import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.util.List;

/**
 * Customer portal home. Deliberately limited: no health score, no internal
 * notes, no deals or billing detail — only the customer-visible surface.
 */
@Route(value = "portal", layout = MainLayout.class)
@PageTitle("Customer portal · CrmOps")
@RouteAccess({Role.CUSTOMER_CONTACT})
public class CustomerDashboardView extends VerticalLayout {

    public CustomerDashboardView(OrganizationService orgService, EscalationService escalationService) {
        addClassName("page");
        Long orgId = SecurityUtils.requireUser().customerOrgId();
        Organization org = orgService.get(orgId);
        List<EscalationService.EscalationView> requests = escalationService.queue(null, null, null, 50);

        add(Ui.pageHeader("Welcome, " + SecurityUtils.requireUser().displayName(),
                "Your workspace with " + org.getName() + "."));

        add(Ui.statGrid(
                Ui.statCard(org.getName(), "Organization", org.getPlan().getName() + " plan", "primary"),
                Ui.statCard(String.valueOf(requests.size()), "Open requests",
                        "with the support team", "info"),
                Ui.statCard(org.getStatus().label(), "Account status",
                        "renewal " + Ui.date(org.getRenewalDate()), "success"),
                Ui.statCard(String.valueOf(org.getSeats()), "Licensed seats",
                        org.getPlan().getName(), "neutral")));

        Div grid = new Div();
        grid.addClassName("two-col");

        Div orgCard = Ui.card();
        H4 orgTitle = new H4("Your organization");
        orgTitle.addClassName("card-title");
        orgCard.add(orgTitle);
        orgCard.add(field("Plan", org.getPlan().getName()));
        orgCard.add(field("Status", org.getStatus().label()));
        orgCard.add(field("Renewal date", Ui.date(org.getRenewalDate())));
        orgCard.add(field("Website", org.getWebsite() == null ? "—" : org.getWebsite()));
        orgCard.add(field("Industry", org.getIndustry() == null ? "—" : org.getIndustry()));
        orgCard.add(field("Country", org.getCountry() == null ? "—" : org.getCountry()));
        orgCard.add(field("Team size", org.getEmployeeCount() == null ? "—" : String.valueOf(org.getEmployeeCount())));
        RouterLink profileLink = new RouterLink("Manage contacts", PortalContactsView.class);
        profileLink.addClassName("link-button");
        orgCard.add(profileLink);

        Div requestsCard = Ui.card();
        H4 reqTitle = new H4("Latest requests");
        reqTitle.addClassName("card-title");
        requestsCard.add(reqTitle);
        if (requests.isEmpty()) {
            requestsCard.add(Ui.emptyState(com.vaadin.flow.component.icon.VaadinIcon.COMMENTS_O,
                    "No open requests", "Use My requests to open a new one."));
        } else {
            for (EscalationService.EscalationView view : requests.stream().limit(6).toList()) {
                Div row = new Div();
                row.addClassName("list-row");
                row.add(Ui.severityBadge(view.severity()));
                Span subject = new Span(view.subject());
                subject.addClassName("list-row-title");
                Span meta = new Span(Ui.relative(view.updatedAt()));
                meta.addClassName("small");
                meta.addClassName("muted");
                row.add(subject, meta, Ui.statusBadge(view.status(), statusLabel(view.status())));
                requestsCard.add(row);
            }
        }
        RouterLink requestsLink = new RouterLink("Open My requests", PortalRequestsView.class);
        requestsLink.addClassName("link-button");
        requestsCard.add(requestsLink);

        grid.add(orgCard, requestsCard);
        add(grid);
    }

    private static Div field(String label, String value) {
        Div div = new Div();
        Paragraph l = new Paragraph(label);
        l.addClassName("detail-field-label");
        Paragraph v = new Paragraph(value);
        v.addClassName("detail-field-value");
        div.add(l, v);
        return div;
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "WAITING_ON_CUSTOMER" -> "Waiting on you";
            case "WAITING_ON_INTERNAL" -> "Being worked on";
            case "RESOLVED" -> "Resolved";
            case "CLOSED" -> "Closed";
            default -> "Open";
        };
    }
}
