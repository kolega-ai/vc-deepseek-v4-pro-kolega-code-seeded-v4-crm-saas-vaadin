package com.crmops.channel.ui;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.service.AuthService;
import com.crmops.channel.service.ValidationException;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Sign in · CrmOps")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    public LoginView(AuthService authService) {
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

        H2 headline = new H2("Welcome back");
        headline.addClassName("login-headline");
        Paragraph sub = new Paragraph("Sign in to manage customer operations.");
        sub.addClassName("login-sub");
        card.add(headline, sub);

        TextField username = new TextField("Email or username");
        username.setWidthFull();
        username.setAutofocus(true);
        username.addClassName("login-field");

        PasswordField password = new PasswordField("Password");
        password.setWidthFull();
        password.addClassName("login-field");

        Span error = new Span();
        error.addClassName("login-error");
        error.setVisible(false);

        Button signIn = new Button("Sign in", event -> {
            error.setVisible(false);
            try {
                var request = (VaadinServletRequest) VaadinRequest.getCurrent();
                var response = (VaadinServletResponse) VaadinResponse.getCurrent();
                authService.login(username.getValue(), password.getValue(),
                        request.getHttpServletRequest(), response.getHttpServletResponse());
                String returnTarget = request.getHeader("Referer");
                if (returnTarget == null || returnTarget.isBlank()) {
                    UI.getCurrent().getPage().setLocation("/");
                } else {
                    UI.getCurrent().getPage().setLocation(returnTarget);
                }
            } catch (ValidationException e) {
                error.setText(e.getMessage());
                error.setVisible(true);
            } catch (DecisionUnavailableException e) {
                error.setText("The authorization service is unavailable right now — please try again shortly.");
                error.setVisible(true);
            }
        });
        signIn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        signIn.setWidthFull();
        signIn.addClassName("login-button");
        signIn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button forgot = new Button("Forgot your password?", event ->
                UI.getCurrent().navigate("forgot-password"));
        forgot.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        forgot.addClassName("login-link");

        card.add(username, password, error, signIn, forgot);

        Div demo = new Div();
        demo.addClassName("demo-credentials");
        H4 demoTitle = new H4("Demo accounts");
        demoTitle.addClassName("demo-title");
        demo.add(demoTitle);
        demo.add(new Paragraph("admin · Admin!Crmops2026 — platform admin"));
        demo.add(new Paragraph("sarah.chan · Manager!Crmops2026 — account manager"));
        demo.add(new Paragraph("lee.support · Support!Crmops2026 — support agent"));
        demo.add(new Paragraph("erin.foley · Customer!Crmops2026 — customer contact"));

        add(card, demo);
    }
}
