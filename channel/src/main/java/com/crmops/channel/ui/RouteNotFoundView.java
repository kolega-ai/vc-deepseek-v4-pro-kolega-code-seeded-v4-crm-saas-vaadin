package com.crmops.channel.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.ErrorParameter;
import com.vaadin.flow.router.HasErrorParameter;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Rendered for unknown routes. Implements HasErrorParameter so Vaadin picks
 * this class up automatically for NotFoundException — no @Route needed.
 */
@PageTitle("Not found · CrmOps")
@AnonymousAllowed
public class RouteNotFoundView extends VerticalLayout implements HasErrorParameter<NotFoundException> {

    public RouteNotFoundView() {
        addClassName("error-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Div card = new Div();
        card.addClassName("error-card");
        H2 title = new H2("Page not found");
        title.addClassName("error-title");
        Paragraph text = new Paragraph("The page you're looking for does not exist or has moved.");
        text.addClassName("error-text");
        Button dashboard = new Button("Go to dashboard", event ->
                UI.getCurrent().getPage().setLocation("/"));
        dashboard.addClassName("primary-button");
        card.add(title, text, dashboard);
        add(card);
    }

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<NotFoundException> parameter) {
        return HttpStatusCode.NOT_FOUND.getCode();
    }
}
