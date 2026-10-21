package com.crmops.channel.ui;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.domain.Plan;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.ReferenceDataService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

/**
 * Plan catalog: seeded reference data shown read-only. Prices are computed
 * server-side only and are never accepted from the client.
 */
@Route("admin/plans")
@RouteAccess({Role.PLATFORM_ADMIN})
@PageTitle("Plans · CrmOps")
public class PlansView extends VerticalLayout {

    public PlansView(ReferenceDataService referenceData, OrganizationService orgService) {
        addClassName("page");
        setPadding(false);
        setWidthFull();

        add(Ui.pageHeader("Plans", "Reference plan catalog — pricing is read server-side only."));

        Map<String, Long> counts = Map.of();
        try {
            counts = orgService.orgCountsByPlan();
        } catch (RuntimeException e) {
            fail(e);
        }

        List<Plan> plans = List.of();
        try {
            plans = referenceData.activePlans();
        } catch (RuntimeException e) {
            fail(e);
        }

        add(Ui.statGrid(
                Ui.statCard("Starter organizations", String.valueOf(counts.getOrDefault("STARTER", 0L)),
                        "On the Starter plan", "info"),
                Ui.statCard("Pro organizations", String.valueOf(counts.getOrDefault("PRO", 0L)),
                        "On the Pro plan", "success"),
                Ui.statCard("Enterprise organizations", String.valueOf(counts.getOrDefault("ENTERPRISE", 0L)),
                        "On the Enterprise plan", "warn")));

        for (Plan plan : plans) {
            add(planCard(plan));
        }

        Paragraph note = new Paragraph("Pricing is seeded reference data and is read server-side only.");
        note.addClassName("small");
        note.addClassName("muted");
        add(note);
    }

    private static Div planCard(Plan plan) {
        Div card = Ui.card();

        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);
        Span title = new Span(plan.getName());
        title.addClassName("card-title");
        titleRow.add(title, Ui.planBadge(plan.getCode()));
        card.add(titleRow);

        Paragraph price = new Paragraph("Base " + Ui.money(plan.getBaseMonthlyPrice()) + "/mo + "
                + Ui.money(plan.getPerSeatMonthlyPrice()) + " per extra seat ("
                + plan.getSeatsIncluded() + " seats included)");
        price.addClassName("small");
        card.add(price);

        Paragraph description = new Paragraph(plan.getDescription());
        description.addClassName("small");
        description.addClassName("muted");
        card.add(description);

        Div features = new Div();
        for (String feature : plan.getFeatures().split(",")) {
            if (!feature.isBlank()) {
                Span chip = new Span(feature.trim());
                chip.addClassName("chip");
                features.add(chip);
            }
        }
        card.add(features);
        return card;
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
