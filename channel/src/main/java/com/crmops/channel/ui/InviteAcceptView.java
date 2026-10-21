package com.crmops.channel.ui;

import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.service.AuthService;
import com.crmops.channel.service.InviteService;
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
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

/**
 * Accepts a staff-sent portal invitation: sets the initial password with the
 * single-use invite token and activates the account.
 */
@Route("invite")
@PageTitle("Accept invitation · CrmOps")
@AnonymousAllowed
public class InviteAcceptView extends VerticalLayout implements BeforeEnterObserver {

    private final InviteService inviteService;
    private final AuthService authService;
    private final Span error = new Span();

    private String token;
    private String contactEmail;

    public InviteAcceptView(InviteService inviteService, AuthService authService) {
        this.inviteService = inviteService;
        this.authService = authService;
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

        H2 headline = new H2("Accept your invitation");
        headline.addClassName("login-headline");
        Paragraph sub = new Paragraph(
                "Welcome to the customer portal. Choose a password to activate your account.");
        sub.addClassName("login-sub");
        card.add(headline, sub);

        PasswordField newPassword = new PasswordField("New password");
        newPassword.setWidthFull();
        newPassword.setAutofocus(true);
        newPassword.setHelperText("At least 12 characters with upper- and lower-case letters and a digit.");
        newPassword.addClassName("login-field");
        PasswordField confirm = new PasswordField("Confirm new password");
        confirm.setWidthFull();
        confirm.addClassName("login-field");

        error.addClassName("login-error");
        error.setVisible(false);

        Button accept = new Button("Accept invitation", event -> {
            error.setVisible(false);
            if (token == null) {
                error.setText("Invalid or expired invitation link.");
                error.setVisible(true);
                return;
            }
            if (!newPassword.getValue().equals(confirm.getValue())) {
                Ui.error("Passwords do not match");
                return;
            }
            try {
                UserAccount account = inviteService.accept(token, contactEmail, newPassword.getValue());
                var request = (VaadinServletRequest) VaadinRequest.getCurrent();
                var response = (VaadinServletResponse) VaadinResponse.getCurrent();
                authService.beginPortalSession(account.getUsername(), newPassword.getValue(),
                        request.getHttpServletRequest(), response.getHttpServletResponse());
                Ui.success("Welcome to the customer portal.");
                UI.getCurrent().getPage().setLocation("/");
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        accept.setWidthFull();
        accept.addClassName("login-button");
        accept.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        card.add(newPassword, confirm, error, accept);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        token = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", List.of()).stream().findFirst().orElse(null);
        contactEmail = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("email", List.of()).stream().findFirst().orElse(null);
        if (token == null) {
            error.setText("Invalid or expired invitation link.");
            error.setVisible(true);
        }
    }
}
