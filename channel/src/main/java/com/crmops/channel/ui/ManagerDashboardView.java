package com.crmops.channel.ui;

import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.DealService;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.OrganizationQueryService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.crmops.common.core.RenewalPolicy.RENEWAL_WINDOW_DAYS;

/** Account manager dashboard: renewal risk, pipeline value, escalations, assigned accounts. */
@Route(value = "manager", layout = MainLayout.class)
@PageTitle("Account manager dashboard · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER})
public class ManagerDashboardView extends VerticalLayout {

    public ManagerDashboardView(OrganizationService orgService, DealService dealService,
                                EscalationService escalationService) {
        addClassName("page");

        String greeting = "Welcome back, " + SecurityUtils.requireUser().displayName()
                + " — here's what needs attention.";
        RouterLink newOrg = new RouterLink("New organization", OrganizationListView.class);
        newOrg.addClassName("primary-button");
        RouterLink pipelineLink = new RouterLink("Open deal pipeline", DealPipelineView.class);
        pipelineLink.addClassName("ghost-button");
        add(Ui.pageHeader("Account manager dashboard", greeting, newOrg, pipelineLink));

        List<Organization> orgs = orgService.search(new OrganizationQueryService.SearchCriteria(
                null, null, null, null, null, "name", "asc"));
        LocalDate today = LocalDate.now();
        List<Organization> dueSoon = orgs.stream()
                .filter(o -> o.getRenewalDate() != null
                        && !o.getRenewalDate().isBefore(today)
                        && !o.getRenewalDate().isAfter(today.plusDays(RENEWAL_WINDOW_DAYS)))
                .toList();
        long openEscalations = escalationService.queue(null, null, null, 500).size();
        DealService.PipelineSummary pipeline = dealService.pipelineSummary();
        long activeOrgs = orgs.stream()
                .filter(o -> o.getStatus() == com.crmops.channel.domain.OrgStatus.ACTIVE).count();
        BigDecimal mrr = orgs.stream()
                .filter(o -> o.getStatus() == com.crmops.channel.domain.OrgStatus.ACTIVE)
                .map(OrganizationService::mrr)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        add(Ui.statGrid(
                Ui.statCard(String.valueOf(orgs.size()), "Assigned accounts", activeOrgs + " active", "primary"),
                Ui.statCard(Ui.money(pipeline.expectedValue()), "Pipeline expected value",
                        pipeline.openDeals() + " open deals", "success"),
                Ui.statCard(String.valueOf(dueSoon.size()), "Renewals within " + RENEWAL_WINDOW_DAYS + " days",
                        "sorted by renewal date", "warn"),
                Ui.statCard(String.valueOf(openEscalations), "Open escalations", "across your accounts", "danger"),
                Ui.statCard(Ui.money(mrr), "Monthly recurring revenue", "active accounts", "info")));

        Div grid = new Div();
        grid.addClassName("two-col");

        Div renewalsCard = Ui.card();
        H4 renewalsTitle = new H4("Renewal risk");
        renewalsTitle.addClassName("card-title");
        renewalsCard.add(renewalsTitle);
        if (dueSoon.isEmpty()) {
            renewalsCard.add(Ui.emptyState(VaadinIcon.CHECK_CIRCLE,
                    "Nothing due in the next " + RENEWAL_WINDOW_DAYS + " days",
                    "Renewals outside the risk window are not shown."));
        } else {
            for (Organization org : dueSoon) {
                Div row = new Div();
                row.addClassName("list-row");
                Span name = new Span(org.getName());
                name.addClassName("list-row-title");
                Span meta = new Span(Ui.date(org.getRenewalDate()) + " · " + org.getPlan().getName());
                meta.addClassName("small");
                meta.addClassName("muted");
                row.add(name, Ui.healthBadge(org.getHealthScore()), meta);
                renewalsCard.add(row);
            }
        }

        Div pipelineCard = Ui.card();
        H4 pipelineTitle = new H4("Pipeline by stage");
        pipelineTitle.addClassName("card-title");
        pipelineCard.add(pipelineTitle);
        String[] stages = {"LEAD", "QUALIFIED", "PROPOSAL", "NEGOTIATION"};
        for (String stage : stages) {
            long count = pipeline.byStage().getOrDefault(stage, 0L);
            Div row = new Div();
            row.addClassName("list-row");
            row.add(Ui.dealStageBadge(stage));
            Span countSpan = new Span(count + (count == 1 ? " deal" : " deals"));
            countSpan.addClassName("small");
            row.add(countSpan);
            pipelineCard.add(row);
        }
        Div total = new Div();
        total.addClassName("list-row");
        Span totalLabel = new Span("Expected value");
        totalLabel.addClassName("list-row-title");
        total.add(totalLabel, new Span(Ui.money(pipeline.expectedValue())));
        pipelineCard.add(total);

        Div escalationsCard = Ui.card();
        H4 escTitle = new H4("Recent escalations");
        escTitle.addClassName("card-title");
        escalationsCard.add(escTitle);
        List<EscalationService.EscalationView> recent = escalationService.queue(null, null, null, 5);
        if (recent.isEmpty()) {
            escalationsCard.add(Ui.emptyState(VaadinIcon.COMMENTS_O, "No open escalations",
                    "New escalations for your accounts appear here."));
        } else {
            for (EscalationService.EscalationView view : recent) {
                Div row = new Div();
                row.addClassName("list-row");
                row.add(Ui.severityBadge(view.severity()));
                Span subject = new Span(view.subject());
                subject.addClassName("list-row-title");
                Span meta = new Span(view.orgName() + " · " + Ui.relative(view.createdAt()));
                meta.addClassName("small");
                meta.addClassName("muted");
                row.add(subject, meta);
                row.addClickListener(event -> UI.getCurrent().navigate("escalations/" + view.id()));
                escalationsCard.add(row);
            }
        }

        grid.add(renewalsCard, pipelineCard, escalationsCard);
        add(grid);
    }
}
