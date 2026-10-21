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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

/**
 * Completes a password reset using the single-use token from the reset email.
 * The token arrives as a query parameter; a missing token renders an error.
 */
@Route("reset-password")
@PageTitle("Set new password · CrmOps")
@AnonymousAllowed
public class ResetPasswordView extends VerticalLayout implements BeforeEnterObserver {

    private final PasswordResetService passwordResetService;
    private final Span error = new Span();

    private String token;

    public ResetPasswordView(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
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

        H2 headline = new H2("Set a new password");
        headline.addClassName("login-headline");
        Paragraph sub = new Paragraph(
                "At least 12 characters with upper- and lower-case letters and a digit.");
        sub.addClassName("login-sub");
        card.add(headline, sub);

        PasswordField newPassword = new PasswordField("New password");
        newPassword.setWidthFull();
        newPassword.setAutofocus(true);
        newPassword.addClassName("login-field");
        PasswordField confirm = new PasswordField("Confirm new password");
        confirm.setWidthFull();
        confirm.addClassName("login-field");

        error.addClassName("login-error");
        error.setVisible(false);

        Button set = new Button("Set password", event -> {
            error.setVisible(false);
            if (token == null) {
                error.setText("Invalid or expired reset link.");
                error.setVisible(true);
                return;
            }
            if (!newPassword.getValue().equals(confirm.getValue())) {
                Ui.error("Passwords do not match");
                return;
            }
            try {
                passwordResetService.completeReset(token, newPassword.getValue());
                Ui.success("Password updated — you can sign in now.");
                UI.getCurrent().navigate("login");
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        set.setWidthFull();
        set.addClassName("login-button");
        set.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button back = new Button("Back to sign in", event -> UI.getCurrent().navigate("login"));
        back.addClassName("login-link");

        card.add(newPassword, confirm, error, set, back);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", List.of()).stream().findFirst().orElse(null);
        if (token == null) {
            error.setText("Invalid or expired reset link.");
            error.setVisible(true);
        }
    }
}
