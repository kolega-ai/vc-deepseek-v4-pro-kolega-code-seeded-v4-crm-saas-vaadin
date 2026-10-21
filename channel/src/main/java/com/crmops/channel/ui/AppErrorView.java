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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.HttpStatusCode;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catch-all for unhandled exceptions. Shows a generic message only — the
 * exception text is never rendered; it is only logged server-side.
 */
@PageTitle("Something went wrong · CrmOps")
@AnonymousAllowed
public class AppErrorView extends VerticalLayout implements HasErrorParameter<Exception> {

    private static final Logger logger = LoggerFactory.getLogger(AppErrorView.class);

    public AppErrorView() {
        addClassName("error-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Div card = new Div();
        card.addClassName("error-card");
        H2 title = new H2("Something went wrong");
        title.addClassName("error-title");
        Paragraph text = new Paragraph(
                "An unexpected error occurred. Please try again — if it persists, "
                        + "contact your administrator.");
        text.addClassName("error-text");
        Button reload = new Button("Reload", event -> UI.getCurrent().getPage().reload());
        reload.addClassName("primary-button");
        card.add(title, text, reload);
        add(card);
    }

    @Override
    public int setErrorParameter(BeforeEnterEvent event, ErrorParameter<Exception> parameter) {
        logger.error("unhandled UI error", parameter.getException());
        return HttpStatusCode.INTERNAL_SERVER_ERROR.getCode();
    }
}
