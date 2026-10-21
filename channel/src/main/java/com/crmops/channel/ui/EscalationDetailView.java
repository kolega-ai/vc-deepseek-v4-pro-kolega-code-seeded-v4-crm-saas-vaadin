package com.crmops.channel.ui;

import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.Role;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.DocumentService;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.NotFoundException;
import com.crmops.channel.service.UserService;
import com.crmops.channel.service.ValidationException;
import com.crmops.channel.ui.components.Ui;
import com.crmops.channel.workflow.EscalationStatusMachine;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.OptionalParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/**
 * Escalation detail with the workflow action bar (explicit state machine),
 * internal notes and customer-visible conversation. Internal comments are
 * rendered only for staff; customers see a single customer-visible thread.
 */
@Route(value = "escalations/:id", layout = MainLayout.class)
@PageTitle("Escalation · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT, Role.CUSTOMER_CONTACT})
public class EscalationDetailView extends VerticalLayout implements HasUrlParameter<Long> {

    private final EscalationService escalationService;
    private final UserService userService;
    private final DocumentService documentService;

    private long escalationId;
    private EscalationService.EscalationView view;
    private boolean staff;
    private boolean canWork; // support agent or platform admin

    public EscalationDetailView(EscalationService escalationService, UserService userService,
                                DocumentService documentService) {
        this.escalationService = escalationService;
        this.userService = userService;
        this.documentService = documentService;
        addClassName("page");
    }

    @Override
    public void setParameter(BeforeEvent event, @OptionalParameter Long parameter) {
        if (parameter == null) {
            event.rerouteTo(EscalationQueueView.class);
            return;
        }
        try {
            view = escalationService.view(parameter);
        } catch (NotFoundException | org.springframework.security.access.AccessDeniedException e) {
            Ui.error("You cannot access this escalation.");
            event.rerouteTo(EscalationQueueView.class);
            return;
        }
        escalationId = parameter;
        Role role = SecurityUtils.requireUser().role();
        staff = role != Role.CUSTOMER_CONTACT;
        canWork = role == Role.SUPPORT_AGENT || role == Role.PLATFORM_ADMIN;
        buildShell();
    }

    private void buildShell() {
        removeAll();
        HorizontalLayout badges = new HorizontalLayout(Ui.severityBadge(view.severity()),
                Ui.statusBadge(view.status(), statusLabel(view.status())));
        badges.setAlignItems(FlexComponent.Alignment.CENTER);
        badges.setSpacing(true);
        add(Ui.pageHeader(view.subject(), view.orgName() + " · opened " + Ui.relative(view.createdAt()), badges));

        add(buildWorkflowBar());

        Div grid = new Div();
        grid.addClassName("two-col");
        grid.add(buildConversation());
        grid.add(buildDetails());
        add(grid);
    }

    private Div buildWorkflowBar() {
        Div bar = new Div();
        bar.addClassName("filter-bar");
        java.util.Set<String> actions = EscalationStatusMachine.allowedActions(
                com.crmops.channel.domain.EscalationStatus.valueOf(view.status()));
        for (String action : actions) {
            // customers may only ever "reply" (handled in the composer, not here)
            if (!canWork) {
                continue;
            }
            Button button = Ui.ghost(statusActionLabel(action));
            button.addClassName(actionClass(action));
            button.addClickListener(event -> {
                try {
                    escalationService.transition(escalationId, action);
                    Ui.success("Status updated: " + statusActionLabel(action));
                    reload();
                } catch (Exception e) {
                    Ui.error(OrganizationListView.messageOf(e));
                }
            });
            bar.add(button);
        }
        if ("RESOLVED".equals(view.status()) || "CLOSED".equals(view.status())) {
            Button reopen = Ui.ghost("Reopen");
            reopen.addClickListener(event -> {
                try {
                    escalationService.reopen(escalationId);
                    Ui.success("Escalation reopened");
                    reload();
                } catch (Exception e) {
                    Ui.error(OrganizationListView.messageOf(e));
                }
            });
            bar.add(reopen);
        }
        if (canWork) {
            Select<UserAccount> assignee = new Select<>();
            assignee.setItemLabelGenerator(u -> u.getDisplayName() + " (" + u.getRole().name() + ")");
            List<UserAccount> agents = userService.staffByRole(Role.SUPPORT_AGENT);
            assignee.setItems(agents);
            assignee.setPlaceholder("Assign to…");
            assignee.setEmptySelectionAllowed(true);
            assignee.addValueChangeListener(event -> {
                if (event.getValue() == null) {
                    return;
                }
                try {
                    escalationService.assign(escalationId, event.getValue().getId());
                    Ui.success("Assigned to " + event.getValue().getDisplayName());
                    reload();
                } catch (Exception e) {
                    Ui.error(OrganizationListView.messageOf(e));
                }
            });
            bar.add(assignee);
        }
        return bar;
    }

    private Div buildConversation() {
        Div card = Ui.card();
        H4 title = new H4(staff ? "Conversation" : "Updates from the team");
        title.addClassName("card-title");
        card.add(title);

        Div thread = new Div();
        thread.addClassName("comment-list");
        List<EscalationService.CommentView> comments = escalationService.comments(escalationId);
        if (comments.isEmpty()) {
            thread.add(Ui.emptyState(com.vaadin.flow.component.icon.VaadinIcon.COMMENTS,
                    "No updates yet", "The conversation starts with your first message."));
        }
        for (EscalationService.CommentView comment : comments) {
            Div entry = new Div();
            entry.addClassName("comment");
            entry.addClassName("INTERNAL".equals(comment.visibility()) ? "internal" : "customer");
            Div meta = new Div();
            meta.addClassName("comment-meta");
            Span author = new Span(comment.authorName());
            Span when = new Span(Ui.relative(comment.createdAt()));
            meta.add(author, when);
            if (staff) {
                meta.add(Ui.badge("INTERNAL".equals(comment.visibility()) ? "Internal" : "Customer-visible",
                        "INTERNAL".equals(comment.visibility()) ? "warning" : "success"));
            }
            Paragraph body = new Paragraph(comment.body());
            body.addClassName("comment-body");
            entry.add(meta, body);
            thread.add(entry);
        }
        card.add(thread);

        // Composer
        if (staff) {
            TextArea internal = new TextArea("Internal note");
            internal.setWidthFull();
            internal.setPlaceholder("Visible to staff only — never shown to the customer");
            Button saveInternal = Ui.ghost("Add internal note");
            saveInternal.addClickListener(event -> {
                try {
                    escalationService.addInternalNote(escalationId, internal.getValue());
                    internal.clear();
                    Ui.success("Internal note added");
                    reload();
                } catch (Exception e) {
                    Ui.error(OrganizationListView.messageOf(e));
                }
            });
            card.add(internal, saveInternal);
        }
        TextArea reply = new TextArea(staff ? "Customer-visible update" : "Reply to the team");
        reply.setWidthFull();
        reply.setPlaceholder(staff ? "Posted to the customer-facing thread" : "Your message will be visible to the support team");
        ComboBox<UserAccount> mention = new ComboBox<>("Pull someone into the thread");
        mention.setWidthFull();
        mention.setClearButtonVisible(true);
        try {
            mention.setItems(userService.threadParticipants());
            mention.setItemLabelGenerator(person -> person.getDisplayName() + " · " + person.getEmail());
        } catch (Exception e) {
            mention.setVisible(false);
        }
        Button send = Ui.primary(staff ? "Post update" : "Send reply");
        send.addClickListener(event -> {
            try {
                String message = mention.getValue() == null ? reply.getValue()
                        : "@" + mention.getValue().getDisplayName() + " " + reply.getValue();
                escalationService.addCustomerVisibleUpdate(escalationId, message);
                reply.clear();
                mention.clear();
                Ui.success("Message sent");
                reload();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        var attachment = Ui.inMemoryUpload(10 * 1024 * 1024, (metadata, data) -> {
            try {
                documentService.attachToRequest(view.orgId(), escalationId,
                        new DocumentService.UploadCommand(metadata.fileName(), null, null,
                                metadata.contentType(), metadata.contentLength(), null, data));
                Ui.success("Attachment added to this account's documents");
                reload();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        card.add(reply, mention, send, attachment);
        return card;
    }

    private Div buildDetails() {
        Div card = Ui.card();
        H4 title = new H4("Details");
        title.addClassName("card-title");
        card.add(title);
        card.add(field("Description", view.description()));
        card.add(renderedSummary());
        card.add(field("Organization", view.orgName()));
        card.add(field("Opened", Ui.dateTime(view.createdAt())));
        card.add(field("Last updated", Ui.dateTime(view.updatedAt())));
        if (staff) {
            card.add(field("Assignee", view.assigneeName() == null ? "Unassigned" : view.assigneeName()));
            card.add(field("SLA due", Ui.dateTime(view.slaDueAt())));
            card.add(field("SLA breached", view.slaBreached() ? "Yes — response overdue" : "No"));
            if (view.resolvedAt() != null) {
                card.add(field("Resolved", Ui.dateTime(view.resolvedAt())));
            }
        }
        return card;
    }

    /** Shows the customer's own formatted summary of the request. */
    private Div renderedSummary() {
        Div div = new Div();
        Paragraph l = new Paragraph("Customer summary");
        l.addClassName("detail-field-label");
        Div summary = new Div();
        summary.addClassName("detail-field-value");
        summary.getElement().setProperty("innerHTML", view.description());
        div.add(l, summary);
        return div;
    }

    private Div field(String label, String value) {
        Div div = new Div();
        Paragraph l = new Paragraph(label);
        l.addClassName("detail-field-label");
        Paragraph v = new Paragraph(value);
        v.addClassName("detail-field-value");
        div.add(l, v);
        return div;
    }

    private void reload() {
        try {
            view = escalationService.view(escalationId);
            buildShell();
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "WAITING_ON_CUSTOMER" -> "Waiting on customer";
            case "WAITING_ON_INTERNAL" -> "Waiting on internal";
            case "RESOLVED" -> "Resolved";
            case "CLOSED" -> "Closed";
            default -> "Open";
        };
    }

    private static String statusActionLabel(String action) {
        return switch (action) {
            case "wait_on_customer" -> "Wait on customer";
            case "wait_on_internal" -> "Wait on internal";
            case "customer_replied" -> "Customer replied";
            case "reopen" -> "Reopen";
            case "resolve" -> "Resolve";
            case "close" -> "Close";
            default -> action;
        };
    }

    private static String actionClass(String action) {
        if ("resolve".equals(action)) {
            return "primary-button";
        }
        if ("close".equals(action)) {
            return "ghost-button";
        }
        return "ghost-button";
    }
}
