package com.crmops.channel.ui;

import com.crmops.channel.security.SecurityUtils;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("access-denied")
@PageTitle("Access denied · CrmOps")
@AnonymousAllowed
public class AccessDeniedView extends VerticalLayout {

    public AccessDeniedView() {
        addClassName("error-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Div card = new Div();
        card.addClassName("error-card");
        H2 title = new H2("Access denied");
        title.addClassName("error-title");
        Paragraph text = new Paragraph(
                "Your account is not allowed to open this page. If you believe this is a mistake, "
                        + "contact your administrator.");
        text.addClassName("error-text");

        Button back = new Button("Go to my dashboard", event -> {
            var principal = SecurityUtils.current();
            if (principal == null) {
                UI.getCurrent().navigate("login");
            } else {
                UI.getCurrent().getPage().setLocation("/");
            }
        });
        back.addClassName("primary-button");
        card.add(title, text, back);
        add(card);
    }
}
