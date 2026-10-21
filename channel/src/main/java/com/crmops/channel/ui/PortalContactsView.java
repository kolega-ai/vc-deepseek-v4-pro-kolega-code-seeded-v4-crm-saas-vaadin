package com.crmops.channel.ui;

import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.ContactService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Customer portal contacts. Customers can add contacts for their own
 * organization and edit only their own linked contact record.
 */
@Route(value = "portal/contacts", layout = MainLayout.class)
@PageTitle("Contacts · CrmOps")
@RouteAccess({Role.CUSTOMER_CONTACT})
public class PortalContactsView extends VerticalLayout {

    private final ContactService contactService;
    private final Long orgId;
    private final Long ownUserId;
    private final Grid<Contact> grid = new Grid<>(Contact.class, false);

    public PortalContactsView(ContactService contactService) {
        this.contactService = contactService;
        this.orgId = SecurityUtils.requireUser().customerOrgId();
        this.ownUserId = SecurityUtils.requireUser().userId();
        addClassName("page");

        Button addContact = Ui.primary("Add contact");
        addContact.addClickListener(event -> openCreateDialog());
        add(Ui.pageHeader("Contacts", "People at your organization.", addContact));

        add(buildGrid());
        refresh();
    }

    private Div buildGrid() {
        Div wrapper = new Div();
        wrapper.addClassName("grid-card");

        grid.addColumn(Contact::fullName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(Contact::getEmail).setHeader("Email").setAutoWidth(true);
        grid.addColumn(contact -> contact.getTitle() == null ? "—" : contact.getTitle())
                .setHeader("Title").setAutoWidth(true);
        grid.addColumn(contact -> contact.getPhone() == null ? "—" : contact.getPhone())
                .setHeader("Phone").setAutoWidth(true);
        grid.addColumn(contact -> contact.getUserId() == null
                        ? Ui.badge("No portal", "plain")
                        : Ui.badge("Portal user", "success"))
                .setHeader("Portal").setAutoWidth(true);
        grid.addComponentColumn(this::buildActions)
                .setHeader("Actions").setAutoWidth(true);

        grid.setAllRowsVisible(true);
        grid.setEmptyStateComponent(Ui.emptyGridContent(VaadinIcon.USERS,
                "No contacts yet", "Add the people you work with."));
        wrapper.add(grid);
        return wrapper;
    }

    private HorizontalLayout buildActions(Contact contact) {
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(true);
        boolean own = contact.getUserId() != null && contact.getUserId().equals(ownUserId);
        if (own) {
            Button edit = Ui.ghost("Edit own");
            edit.addClickListener(event -> openEditOwnDialog(contact));
            actions.add(edit);
        }
        return actions;
    }

    private void refresh() {
        try {
            grid.setItems(contactService.list(orgId));
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add contact");
        dialog.setWidth("480px");

        TextField firstName = new TextField("First name");
        firstName.setWidthFull();
        firstName.setAutofocus(true);
        TextField lastName = new TextField("Last name");
        lastName.setWidthFull();
        TextField email = new TextField("Email");
        email.setWidthFull();
        TextField phone = new TextField("Phone");
        phone.setWidthFull();
        TextField title = new TextField("Title");
        title.setWidthFull();

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Add contact");
        save.addClickListener(event -> {
            try {
                contactService.create(orgId, firstName.getValue(), lastName.getValue(),
                        email.getValue(), phone.getValue(), title.getValue(), "", false, false);
                dialog.close();
                Ui.success("Contact added");
                refresh();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();

        VerticalLayout content = new VerticalLayout(firstName, lastName, email, phone, title, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }

    private void openEditOwnDialog(Contact contact) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Edit your details");

        TextField phone = new TextField("Phone");
        phone.setValue(contact.getPhone() == null ? "" : contact.getPhone());
        phone.setWidthFull();
        TextField title = new TextField("Title");
        title.setValue(contact.getTitle() == null ? "" : contact.getTitle());
        title.setWidthFull();

        Button cancel = Ui.ghost("Cancel");
        cancel.addClickListener(event -> dialog.close());
        Button save = Ui.primary("Save");
        save.addClickListener(event -> {
            try {
                contactService.updateOwnContact(contact.getId(), phone.getValue(), title.getValue());
                dialog.close();
                Ui.success("Your details were updated");
                refresh();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        HorizontalLayout footer = new HorizontalLayout(cancel, save);
        footer.addClassName("dialog-footer");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();

        VerticalLayout content = new VerticalLayout(phone, title, footer);
        content.addClassName("form-dialog-content");
        dialog.add(content);
        dialog.open();
    }
}
