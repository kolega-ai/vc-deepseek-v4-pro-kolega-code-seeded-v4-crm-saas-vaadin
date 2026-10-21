package com.crmops.channel.ui;

import com.crmops.channel.domain.Deal;
import com.crmops.channel.domain.DealStage;
import com.crmops.channel.domain.DealType;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.DealService;
import com.crmops.channel.service.OrganizationQueryService;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.UserService;
import com.crmops.channel.ui.components.Ui;
import com.crmops.channel.workflow.DealStageMachine;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deal pipeline across the caller's organization scope: expected value,
 * per-stage counts and stage transitions through the explicit machine edges.
 */
@Route(value = "deals", layout = MainLayout.class)
@PageTitle("Deal pipeline · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER})
public class DealPipelineView extends VerticalLayout {

    private final DealService dealService;
    private final OrganizationService orgService;
    private final UserService userService;
    private final Grid<Deal> grid = new Grid<>(Deal.class, false);

    private Select<String> stageFilter;
    private Select<String> typeFilter;
    private Select<com.crmops.channel.domain.Organization> orgFilter;

    public DealPipelineView(DealService dealService, OrganizationService orgService, UserService userService) {
        this.dealService = dealService;
        this.orgService = orgService;
        this.userService = userService;
        addClassName("page");
        Button newDeal = Ui.primary("New deal");
        newDeal.addClickListener(event -> openDealDialog());
        add(Ui.pageHeader("Deal pipeline", "New business and renewals across your accounts.", newDeal));
        add(buildFilterBar());
        add(buildGrid());
        refresh();
    }

    private Div buildFilterBar() {
        Div bar = new Div();
        bar.addClassName("filter-bar");
        stageFilter = new Select<>();
        stageFilter.setItems("All stages", "LEAD", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "CLOSED_WON",
                "CLOSED_LOST", "RENEWAL_PENDING");
        stageFilter.setValue("All stages");
        stageFilter.setWidth("160px");
        typeFilter = new Select<>();
        typeFilter.setItems("All types", "NEW_BUSINESS", "RENEWAL");
        typeFilter.setValue("All types");
        typeFilter.setWidth("150px");
        orgFilter = new Select<>();
        orgFilter.setLabel("Organization");
        orgFilter.setItemLabelGenerator(com.crmops.channel.domain.Organization::getName);
        orgFilter.setEmptySelectionAllowed(true);
        orgFilter.setPlaceholder("All organizations");
        try {
            orgFilter.setItems(orgService.search(new OrganizationQueryService.SearchCriteria(
                    null, null, null, null, null, "name", "asc")));
        } catch (Exception ignored) {
            // scoping errors surface on refresh
        }
        orgFilter.setWidth("240px");
        Button apply = Ui.primary("Apply");
        apply.addClickListener(event -> refresh());
        Button reset = Ui.ghost("Reset");
        reset.addClickListener(event -> {
            stageFilter.setValue("All stages");
            typeFilter.setValue("All types");
            orgFilter.clear();
            refresh();
        });
        bar.add(orgFilter, stageFilter, typeFilter, apply, reset);
        return bar;
    }

    private Div buildGrid() {
        Div wrapper = new Div();
        wrapper.addClassName("grid-card");
        grid.addColumn(deal -> {
            Div cell = new Div();
            Span name = new Span(deal.getName());
            name.addClassName("list-row-title");
            Span org = new Span(deal.getOrg().getName());
            org.addClassName("small");
            org.addClassName("muted");
            cell.add(name, org);
            return cell;
        }).setHeader("Deal").setAutoWidth(true);
        grid.addColumn(deal -> Ui.badge(deal.getDealType().label(), "neutral"))
                .setHeader("Type").setAutoWidth(true);
        grid.addColumn(deal -> Ui.dealStageBadge(deal.getStage().name()))
                .setHeader("Stage").setAutoWidth(true);
        grid.addColumn(deal -> Ui.money(deal.getAmount())).setHeader("Amount").setAutoWidth(true);
        grid.addColumn(deal -> deal.getProbability() + "%").setHeader("Prob.").setAutoWidth(true);
        grid.addColumn(deal -> Ui.money(deal.expectedValue())).setHeader("Expected").setAutoWidth(true);
        grid.addColumn(deal -> Ui.date(deal.getCloseDate())).setHeader("Close date").setAutoWidth(true);
        grid.addComponentColumn(deal -> {
            HorizontalLayout actions = new HorizontalLayout();
            for (String action : DealStageMachine.allowedActions(deal.getStage()).stream().sorted().toList()) {
                Button move = Ui.ghost(dealActionLabel(action));
                move.addClickListener(event -> {
                    try {
                        dealService.transition(deal.getId(), action);
                        Ui.success("Deal moved");
                        refresh();
                    } catch (Exception e) {
                        Ui.error(OrganizationListView.messageOf(e));
                    }
                });
                actions.add(move);
            }
            return actions;
        }).setHeader("Stage actions").setAutoWidth(true);
        grid.addItemClickListener(event -> UI.getCurrent().navigate("organizations/" + event.getItem().getOrg().getId()));
        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.CHART, "No deals match",
                "Adjust the filters or create a deal."));
        wrapper.add(grid);
        return wrapper;
    }

    private void refresh() {
        try {
            List<com.crmops.channel.domain.Organization> orgs = orgService.search(
                    new OrganizationQueryService.SearchCriteria(null, null, null, null, null, "name", "asc"));
            Map<Long, Deal> filtered = new HashMap<>();
            for (com.crmops.channel.domain.Organization org : orgs) {
                for (Deal deal : dealService.list(org.getId())) {
                    filtered.put(deal.getId(), deal);
                }
            }
            List<Deal> items = filtered.values().stream()
                    .filter(deal -> "All stages".equals(stageFilter.getValue())
                            || deal.getStage().name().equals(stageFilter.getValue()))
                    .filter(deal -> "All types".equals(typeFilter.getValue())
                            || deal.getDealType().name().equals(typeFilter.getValue()))
                    .filter(deal -> orgFilter.getValue() == null
                            || deal.getOrg().getId().equals(orgFilter.getValue().getId()))
                    .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                    .toList();
            grid.setItems(items);
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private void openDealDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New deal");
        dialog.setWidth("460px");
        Select<com.crmops.channel.domain.Organization> orgSelect = new Select<>();
        orgSelect.setLabel("Organization");
        orgSelect.setItemLabelGenerator(com.crmops.channel.domain.Organization::getName);
        try {
            orgSelect.setItems(orgService.search(new OrganizationQueryService.SearchCriteria(
                    null, null, null, null, null, "name", "asc")));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
            return;
        }
        orgSelect.setWidthFull();
        TextField name = new TextField("Deal name");
        name.setWidthFull();
        Select<DealType> type = new Select<>();
        type.setLabel("Type");
        type.setItemLabelGenerator(DealType::label);
        type.setItems(DealType.values());
        type.setValue(DealType.NEW_BUSINESS);
        type.setWidthFull();
        TextField amount = new TextField("Amount (USD)");
        amount.setWidthFull();
        IntegerField probability = new IntegerField("Probability (%)");
        probability.setWidthFull();
        probability.setMin(0);
        probability.setMax(100);
        probability.setValue(50);
        DatePicker closeDate = new DatePicker("Close date");
        closeDate.setWidthFull();
        TextArea notes = new TextArea("Notes");
        notes.setWidthFull();
        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Create");
        save.addClickListener(event -> {
            try {
                BigDecimal value = new BigDecimal(amount.getValue().isBlank() ? "0" : amount.getValue());
                dealService.create(orgSelect.getValue().getId(), name.getValue(), type.getValue(),
                        value, probability.getValue(), closeDate.getValue(), notes.getValue());
                dialog.close();
                Ui.success("Deal created");
                refresh();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        VerticalLayout content = new VerticalLayout(orgSelect, name, type, amount, probability, closeDate, notes, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private String dealActionLabel(String action) {
        return switch (action) {
            case "qualify" -> "Qualify";
            case "propose" -> "Propose";
            case "negotiate" -> "Negotiate";
            case "win" -> "Won";
            case "lose" -> "Lost";
            case "mark_renewal_pending" -> "Renewal pending";
            case "renew" -> "Renewed";
            case "renegotiate" -> "Renegotiate";
            case "recycle" -> "Recycle";
            default -> action;
        };
    }
}
