package com.crmops.channel.ui;

import com.crmops.channel.decision.DecisionUnavailableException;
import com.crmops.channel.domain.AssignmentRelation;
import com.crmops.channel.domain.OrgAssignment;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.service.UserService;
import com.crmops.channel.service.ValidationException;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * Platform-admin user management: search, role changes, enable/disable,
 * organization assignments and admin password resets. Every mutation is
 * enforced server-side by the decision tier and lands in the audit log.
 */
@Route("admin/users")
@RouteAccess({Role.PLATFORM_ADMIN})
@PageTitle("Users · CrmOps")
public class UsersAdminView extends VerticalLayout {

    private static final int PAGE_SIZE = 20;

    private final UserService userService;

    private final TextField searchField = new TextField("Search");
    private final Select<String> roleFilter = Ui.select("Role", "All", "PLATFORM_ADMIN",
            "ACCOUNT_MANAGER", "SUPPORT_AGENT", "CUSTOMER_CONTACT");
    private final Select<String> statusFilter = Ui.select("Status", "All", "Enabled", "Disabled");
    private final Grid<UserAccount> grid = new Grid<>(UserAccount.class, false);
    private final Span pageLabel = new Span();
    private final Button prevButton = Ui.ghost("Prev");
    private final Button nextButton = Ui.ghost("Next");
    private int page;
    private int totalPages = 1;

    public UsersAdminView(UserService userService) {
        this.userService = userService;
        addClassName("page");
        setPadding(false);
        setWidthFull();

        roleFilter.setValue("All");
        statusFilter.setValue("All");

        Button apply = Ui.primary("Apply");
        apply.getElement().setAttribute("aria-label", "Apply user filters");
        apply.addClickListener(event -> {
            page = 0;
            refresh();
        });

        Div filterBar = new Div(searchField, roleFilter, statusFilter, apply);
        filterBar.addClassName("filter-bar");

        Button newUser = Ui.primary("New user");
        newUser.getElement().setAttribute("aria-label", "Create a new user");
        newUser.addClickListener(event -> openNewUserDialog());

        add(Ui.pageHeader("Users", "Manage staff accounts, roles and organization access.", newUser));
        add(filterBar);

        grid.addColumn(user -> {
            VerticalLayout cell = new VerticalLayout();
            cell.setPadding(false);
            cell.setSpacing(false);
            cell.add(new Span(user.getDisplayName()));
            Span username = new Span("@" + user.getUsername());
            username.addClassName("small");
            username.addClassName("muted");
            cell.add(username);
            return cell;
        }).setHeader("User").setAutoWidth(true);
        grid.addColumn(UserAccount::getEmail).setHeader("Email").setAutoWidth(true);
        grid.addColumn(user -> Ui.statusBadge(user.getRole().name(), roleLabel(user.getRole())))
                .setHeader("Role").setAutoWidth(true);
        grid.addColumn(user -> user.isEnabled()
                        ? Ui.badge("Enabled", "success") : Ui.badge("Disabled", "danger"))
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(user -> user.getCustomerOrgId() == null ? "—" : String.valueOf(user.getCustomerOrgId()))
                .setHeader("Org").setAutoWidth(true);
        grid.addColumn(user -> Ui.dateTime(user.getCreatedAt())).setHeader("Created").setAutoWidth(true);
        grid.addComponentColumn(user -> {
            Button edit = Ui.ghost("Edit");
            edit.getElement().setAttribute("aria-label", "Edit user " + user.getUsername());
            edit.addClickListener(event -> openEditDialog(user));
            return edit;
        }).setHeader("Actions").setAutoWidth(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.USERS, "No users found",
                "Adjust the filters or create a new user."));

        Div gridCard = new Div(grid);
        gridCard.addClassName("grid-card");
        add(gridCard);

        prevButton.getElement().setAttribute("aria-label", "Previous page");
        prevButton.addClickListener(event -> {
            if (page > 0) {
                page--;
                refresh();
            }
        });
        nextButton.getElement().setAttribute("aria-label", "Next page");
        nextButton.addClickListener(event -> {
            if (page < totalPages - 1) {
                page++;
                refresh();
            }
        });
        pageLabel.addClassName("small");
        pageLabel.addClassName("muted");
        HorizontalLayout pager = new HorizontalLayout(prevButton, pageLabel, nextButton);
        pager.setAlignItems(FlexComponent.Alignment.CENTER);
        add(pager);

        refresh();
    }

    private void refresh() {
        String roleValue = roleFilter.getValue();
        Role role = "All".equals(roleValue) ? null : Role.valueOf(roleValue);
        Boolean enabled = switch (statusFilter.getValue()) {
            case "Enabled" -> Boolean.TRUE;
            case "Disabled" -> Boolean.FALSE;
            default -> null;
        };
        try {
            Page<UserAccount> result = userService.search(
                    searchField.isEmpty() ? null : searchField.getValue().trim(),
                    role, enabled, PageRequest.of(page, PAGE_SIZE));
            grid.setItems(result.getContent());
            grid.setAllRowsVisible(true);
            totalPages = Math.max(1, result.getTotalPages());
            pageLabel.setText("Page " + (page + 1) + " of " + totalPages);
            prevButton.setEnabled(page > 0);
            nextButton.setEnabled(page < totalPages - 1);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void openNewUserDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("New user");
        dialog.setWidth("480px");

        TextField username = new TextField("Username");
        username.setWidthFull();
        TextField displayName = new TextField("Display name");
        displayName.setWidthFull();
        TextField email = new TextField("Email");
        email.setWidthFull();
        Select<Role> role = new Select<>();
        role.setLabel("Role");
        role.setItems(Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT);
        role.setItemLabelGenerator(UsersAdminView::roleLabel);
        role.setEmptySelectionAllowed(false);
        role.setValue(Role.ACCOUNT_MANAGER);
        PasswordField password = new PasswordField("Initial password");
        password.setHelperText("At least 12 characters with upper- and lower-case letters and a digit.");
        password.setWidthFull();

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button create = Ui.primary("Create user");
        create.getElement().setAttribute("aria-label", "Create user");
        create.addClickListener(event -> {
            if (username.isEmpty() || displayName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Ui.error("All fields are required");
                return;
            }
            try {
                UserService.validatePassword(password.getValue());
            } catch (ValidationException e) {
                Ui.error(e.getMessage());
                return;
            }
            try {
                userService.createUser(username.getValue(), displayName.getValue(), email.getValue(),
                        role.getValue(), null, null, null, password.getValue());
                Ui.success("User created");
                dialog.close();
                refresh();
            } catch (RuntimeException e) {
                fail(e);
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, create);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout form = new VerticalLayout(username, displayName, email, role, password);
        form.addClassName("form-dialog-content");
        dialog.add(form, footer);
        dialog.open();
    }

    private void openEditDialog(UserAccount user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit " + user.getUsername());
        dialog.setWidth("540px");

        Select<Role> role = new Select<>();
        role.setLabel("Role");
        role.setItems(Role.values());
        role.setItemLabelGenerator(UsersAdminView::roleLabel);
        role.setEmptySelectionAllowed(false);
        role.setValue(user.getRole());

        Checkbox enabled = new Checkbox("Account enabled");
        enabled.setValue(user.isEnabled());

        TextField title = new TextField("Title");
        title.setValue(user.getTitle() == null ? "" : user.getTitle());
        TextField phone = new TextField("Phone");
        phone.setValue(user.getPhone() == null ? "" : user.getPhone());

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.getElement().setAttribute("aria-label", "Save changes for " + user.getUsername());
        save.addClickListener(event -> {
            try {
                if (role.getValue() != null && role.getValue() != user.getRole()) {
                    userService.changeRole(user.getId(), role.getValue());
                }
                if (enabled.getValue() != user.isEnabled()) {
                    userService.setEnabled(user.getId(), enabled.getValue());
                }
                userService.updateUserProfile(user.getId(), user.getDisplayName(),
                        title.isEmpty() ? null : title.getValue().trim(),
                        phone.isEmpty() ? null : phone.getValue().trim(),
                        user.getEmail());
                Ui.success("Saved");
                dialog.close();
                refresh();
            } catch (RuntimeException e) {
                fail(e);
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout form = new VerticalLayout(role, enabled, title, phone);
        form.addClassName("form-dialog-content");

        Div divider = new Div();
        divider.addClassName("divider");

        Paragraph assignTitle = new Paragraph("Organization assignments");
        assignTitle.addClassName("section-title");

        VerticalLayout assignments = new VerticalLayout();
        assignments.setPadding(false);
        assignments.setSpacing(false);
        reloadAssignments(assignments, user);

        NumberField orgId = new NumberField("Organization id");
        orgId.setMin(1);
        Select<AssignmentRelation> relation = new Select<>();
        relation.setLabel("Relation");
        relation.setItems(AssignmentRelation.values());
        relation.setItemLabelGenerator(AssignmentRelation::label);
        relation.setEmptySelectionAllowed(false);
        relation.setValue(AssignmentRelation.SUPPORT);
        Button assign = Ui.primary("Assign org");
        assign.getElement().setAttribute("aria-label", "Assign organization to " + user.getUsername());
        assign.addClickListener(event -> {
            if (orgId.getValue() == null) {
                Ui.error("Enter an organization id");
                return;
            }
            try {
                userService.assignOrg(user.getId(), orgId.getValue().longValue(), relation.getValue());
                Ui.success("Org assigned");
                orgId.clear();
                reloadAssignments(assignments, user);
            } catch (RuntimeException e) {
                fail(e);
            }
        });
        HorizontalLayout assignRow = new HorizontalLayout(orgId, relation, assign);
        assignRow.setAlignItems(FlexComponent.Alignment.END);

        Button resetPassword = Ui.ghost("Reset password");
        resetPassword.getElement().setAttribute("aria-label", "Reset password for " + user.getUsername());
        resetPassword.addClickListener(event -> openResetPasswordDialog(user));

        dialog.add(form, footer, divider, assignTitle, assignments, assignRow, resetPassword);
        dialog.open();
    }

    private void reloadAssignments(VerticalLayout assignments, UserAccount user) {
        assignments.removeAll();
        try {
            List<OrgAssignment> current = userService.assignments(user.getId());
            if (current.isEmpty()) {
                Span none = new Span("No organization assignments.");
                none.addClassName("small");
                none.addClassName("muted");
                assignments.add(none);
            }
            for (OrgAssignment assignment : current) {
                HorizontalLayout row = new HorizontalLayout();
                row.setWidthFull();
                row.setAlignItems(FlexComponent.Alignment.CENTER);
                Span label = new Span("Org #" + assignment.getOrgId() + " · " + assignment.getRelation().label());
                label.addClassName("small");
                Button remove = Ui.ghost("Remove");
                remove.getElement().setAttribute("aria-label",
                        "Remove org " + assignment.getOrgId() + " from " + user.getUsername());
                remove.addClickListener(event -> {
                    try {
                        userService.removeAssignment(user.getId(), assignment.getOrgId());
                        Ui.success("Assignment removed");
                        reloadAssignments(assignments, user);
                    } catch (RuntimeException e) {
                        fail(e);
                    }
                });
                row.add(label, remove);
                assignments.add(row);
            }
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void openResetPasswordDialog(UserAccount user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Reset password for " + user.getUsername());
        dialog.setWidth("420px");

        PasswordField password = new PasswordField("New password");
        password.setHelperText("At least 12 characters with upper- and lower-case letters and a digit.");
        password.setWidthFull();

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button reset = Ui.primary("Reset password");
        reset.getElement().setAttribute("aria-label", "Confirm password reset for " + user.getUsername());
        reset.addClickListener(event -> {
            try {
                UserService.validatePassword(password.getValue());
            } catch (ValidationException e) {
                Ui.error(e.getMessage());
                return;
            }
            try {
                userService.adminResetPassword(user.getId(), password.getValue());
                Ui.success("Password reset — a notification email was queued");
                dialog.close();
            } catch (RuntimeException e) {
                fail(e);
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, reset);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VerticalLayout form = new VerticalLayout(password);
        form.addClassName("form-dialog-content");
        dialog.add(form, footer);
        dialog.open();
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

    private static String roleLabel(Role role) {
        return switch (role) {
            case PLATFORM_ADMIN -> "Platform admin";
            case ACCOUNT_MANAGER -> "Account manager";
            case SUPPORT_AGENT -> "Support agent";
            case CUSTOMER_CONTACT -> "Customer contact";
        };
    }
}
