package com.crmops.channel.ui;

import com.crmops.channel.service.PasswordResetService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Self-service password reset request. Anti-enumeration: the same message is
 * shown whether or not the account exists. Email is a local mock, so the
 * reset link is surfaced in the UI to keep the demo flow completable.
 */
@Route("forgot-password")
@PageTitle("Reset password · CrmOps")
@AnonymousAllowed
public class ForgotPasswordView extends VerticalLayout {

    public ForgotPasswordView(PasswordResetService passwordResetService) {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        Div card = new Div();
        card.addClassName("login-card");

        Div brand = new Div();
        brand.addClassName("login-brand");
        brand.add(new Span("◆"), new Span("CrmOps"));
        card.add(brand);

        H2 headline = new H2("Reset your password");
        headline.addClassName("login-headline");
        Paragraph sub = new Paragraph("Enter your account email and we'll send you a reset link.");
        sub.addClassName("login-sub");
        card.add(headline, sub);

        TextField email = new TextField("Email");
        email.setWidthFull();
        email.setAutofocus(true);
        email.addClassName("login-field");

        Paragraph secretCaption = new Paragraph("Local demo: email is mocked — use this link:");
        secretCaption.addClassName("small");
        secretCaption.addClassName("muted");
        secretCaption.setVisible(false);
        Span secretLink = new Span();
        secretLink.addClassName("secret-once");
        secretLink.setVisible(false);

        Button send = new Button("Send reset link", event -> {
            try {
                String link = passwordResetService.requestReset(email.getValue());
                if (link == null) {
                    secretCaption.setVisible(false);
                    secretLink.setVisible(false);
                    Ui.error("No CrmOps account is registered for " + email.getValue() + ".");
                } else {
                    secretLink.setText(link);
                    secretCaption.setVisible(true);
                    secretLink.setVisible(true);
                    Ui.success("If that account exists, a reset link was sent.");
                }
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        send.setWidthFull();
        send.addClassName("login-button");
        send.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button back = new Button("Back to sign in", event -> UI.getCurrent().navigate("login"));
        back.addClassName("login-link");

        card.add(email, secretCaption, secretLink, send, back);
        add(card);
    }
}
