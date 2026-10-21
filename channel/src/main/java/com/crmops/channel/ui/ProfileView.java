package com.crmops.channel.ui;

import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.PasswordResetService;
import com.crmops.channel.service.UserService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Self-service profile and password change, available to every role.
 */
@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT, Role.CUSTOMER_CONTACT})
public class ProfileView extends VerticalLayout {

    private final UserService userService;
    private final PasswordResetService passwordResetService;

    public ProfileView(UserService userService, PasswordResetService passwordResetService) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        addClassName("page");

        UserAccount user = userService.get(SecurityUtils.requireUser().userId());
        add(Ui.pageHeader("Profile", "Your account settings."));

        Div grid = new Div();
        grid.addClassName("two-col");
        grid.add(buildProfileCard(user));
        grid.add(buildPasswordCard());
        add(grid);
    }

    private Div buildProfileCard(UserAccount user) {
        Div card = Ui.card();
        H4 title = new H4("Profile");
        title.addClassName("card-title");
        card.add(title);

        TextField displayName = new TextField("Display name");
        displayName.setValue(user.getDisplayName() == null ? "" : user.getDisplayName());
        displayName.setWidthFull();
        TextField titleField = new TextField("Title");
        titleField.setValue(user.getTitle() == null ? "" : user.getTitle());
        titleField.setWidthFull();
        TextField phone = new TextField("Phone");
        phone.setValue(user.getPhone() == null ? "" : user.getPhone());
        phone.setWidthFull();

        Button save = Ui.primary("Save profile");
        save.addClickListener(event -> {
            try {
                userService.updateOwnProfile(displayName.getValue(), titleField.getValue(), phone.getValue());
                Ui.success("Profile updated");
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        card.add(displayName, titleField, phone, save);

        Div divider = new Div();
        divider.addClassName("divider");
        card.add(divider);

        card.add(field("Email", user.getEmail() == null ? "—" : user.getEmail()));
        card.add(field("Username", user.getUsername() == null ? "—" : user.getUsername()));
        card.add(roleBadge(user.getRole()));
        return card;
    }

    private Div buildPasswordCard() {
        Div card = Ui.card();
        H4 title = new H4("Change password");
        title.addClassName("card-title");
        card.add(title);

        PasswordField current = new PasswordField("Current password");
        current.setWidthFull();
        PasswordField newPassword = new PasswordField("New password");
        newPassword.setWidthFull();
        newPassword.setHelperText("At least 12 characters with upper- and lower-case letters and a digit.");
        PasswordField confirm = new PasswordField("Confirm new password");
        confirm.setWidthFull();

        Button change = Ui.primary("Change password");
        change.addClickListener(event -> {
            if (!newPassword.getValue().equals(confirm.getValue())) {
                Ui.error("Passwords do not match");
                return;
            }
            try {
                passwordResetService.changeOwnPassword(current.getValue(), newPassword.getValue());
                current.clear();
                newPassword.clear();
                confirm.clear();
                Ui.success("Password changed");
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        card.add(current, newPassword, confirm, change);
        return card;
    }

    private static Div field(String label, String value) {
        Div div = new Div();
        Paragraph l = new Paragraph(label);
        l.addClassName("detail-field-label");
        Paragraph v = new Paragraph(value);
        v.addClassName("detail-field-value");
        div.add(l, v);
        return div;
    }

    private static Div roleBadge(Role role) {
        Div div = new Div();
        Paragraph l = new Paragraph("Role");
        l.addClassName("detail-field-label");
        div.add(l);
        div.add(Ui.badge(roleLabel(role), "neutral"));
        return div;
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case PLATFORM_ADMIN -> "Platform admin";
            case ACCOUNT_MANAGER -> "Account manager";
            case SUPPORT_AGENT -> "Support agent";
            case CUSTOMER_CONTACT -> "Customer contact";
        };
    }
}
