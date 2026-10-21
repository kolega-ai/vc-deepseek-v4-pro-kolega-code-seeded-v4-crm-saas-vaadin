package com.crmops.channel.ui;

import com.crmops.channel.domain.AuditEvent;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.AuditService;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.OrganizationQueryService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.UserQuerySupport;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.RenewalPolicy.RENEWAL_WINDOW_DAYS;

/** Platform admin dashboard: whole-portfolio health, renewals, users, recent audit activity. */
@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Platform dashboard · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN})
public class AdminDashboardView extends VerticalLayout {

    public AdminDashboardView(OrganizationService orgService, EscalationService escalationService,
                              AuditService auditService, com.crmops.channel.repo.UserAccountRepository userRepo) {
        addClassName("page");
        RouterLink users = new RouterLink("Manage users", UsersAdminView.class);
        users.addClassName("primary-button");
        add(Ui.pageHeader("Platform dashboard", "Everything across all tenants.", users));

        List<Organization> orgs = orgService.search(new OrganizationQueryService.SearchCriteria(
                null, null, null, null, null, "name", "asc"));
        long active = orgs.stream().filter(o -> o.getStatus() == com.crmops.channel.domain.OrgStatus.ACTIVE).count();
        long trial = orgs.stream().filter(o -> o.getStatus() == com.crmops.channel.domain.OrgStatus.TRIAL).count();
        long atRisk = orgs.stream().filter(o -> "AT_RISK".equals(o.healthBucket())
                || "CRITICAL".equals(o.healthBucket())).count();
        BigDecimal mrr = orgs.stream()
                .filter(o -> o.getStatus() == com.crmops.channel.domain.OrgStatus.ACTIVE)
                .map(OrganizationService::mrr)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Long> sla = escalationService.slaStats();
        long userCount = userRepo.count();

        add(Ui.statGrid(
                Ui.statCard(String.valueOf(orgs.size()), "Organizations", active + " active · " + trial + " trial", "primary"),
                Ui.statCard(Ui.money(mrr), "Monthly recurring revenue", "active accounts", "success"),
                Ui.statCard(String.valueOf(atRisk), "Health at risk", "score below 65", "warn"),
                Ui.statCard(String.valueOf(sla.get("open")), "Open escalations",
                        sla.get("breached") + " SLA breached", "danger"),
                Ui.statCard(String.valueOf(userCount), "Users", "all roles", "info")));

        Div grid = new Div();
        grid.addClassName("two-col");

        Div renewalsCard = Ui.card();
        H4 rTitle = new H4("Renewals due within " + RENEWAL_WINDOW_DAYS + " days");
        rTitle.addClassName("card-title");
        renewalsCard.add(rTitle);
        List<Organization> dueSoon = orgs.stream()
                .filter(o -> o.getRenewalDate() != null
                        && !o.getRenewalDate().isBefore(LocalDate.now())
                        && !o.getRenewalDate().isAfter(LocalDate.now().plusDays(RENEWAL_WINDOW_DAYS)))
                .toList();
        if (dueSoon.isEmpty()) {
            renewalsCard.add(Ui.emptyState(com.vaadin.flow.component.icon.VaadinIcon.CHECK_CIRCLE,
                    "No renewals due", "Nothing expires in the next " + RENEWAL_WINDOW_DAYS + " days."));
        } else {
            for (Organization org : dueSoon) {
                Div row = new Div();
                row.addClassName("list-row");
                row.add(Ui.planBadge(org.getPlan().getCode()));
                Span name = new Span(org.getName());
                name.addClassName("list-row-title");
                row.add(name, Ui.healthBadge(org.getHealthScore()),
                        new Span(Ui.date(org.getRenewalDate())));
                renewalsCard.add(row);
            }
        }

        Div auditCard = Ui.card();
        H4 aTitle = new H4("Recent audit activity");
        aTitle.addClassName("card-title");
        auditCard.add(aTitle);
        List<AuditEvent> events = auditService.recent(12);
        for (AuditEvent event : events) {
            Div row = new Div();
            row.addClassName("list-row");
            Span action = new Span(event.getAction());
            action.addClassName("mono");
            action.addClassName("small");
            Span actor = new Span((event.getActorUsername() == null ? "system" : event.getActorUsername())
                    + " · " + Ui.relative(event.getOccurredAt()));
            actor.addClassName("small");
            actor.addClassName("muted");
            row.add(action, actor);
            auditCard.add(row);
        }
        RouterLink allAudit = new RouterLink("Open audit log", AuditLogView.class);
        allAudit.addClassName("link-button");
        auditCard.add(allAudit);

        grid.add(renewalsCard, auditCard);
        add(grid);
    }
}
