package com.crmops.channel.ui;

import com.crmops.channel.domain.Notification;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.NotificationService;
import com.crmops.channel.ui.components.Ui;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.util.List;

/**
 * In-app notifications for the acting user. Unread rows get a left accent;
 * rows link through to their target route.
 */
@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications · CrmOps")
@RouteAccess({Role.PLATFORM_ADMIN, Role.ACCOUNT_MANAGER, Role.SUPPORT_AGENT, Role.CUSTOMER_CONTACT})
public class NotificationsView extends VerticalLayout {

    private final NotificationService notificationService;
    private final long userId;
    private final Div list = new Div();

    public NotificationsView(NotificationService notificationService) {
        this.notificationService = notificationService;
        this.userId = SecurityUtils.requireUser().userId();
        addClassName("page");

        Button markAll = Ui.ghost("Mark all read");
        markAll.addClickListener(event -> {
            try {
                notificationService.markAllRead(userId);
                refresh();
            } catch (Exception e) {
                Ui.error(OrganizationListView.messageOf(e));
            }
        });
        add(Ui.pageHeader("Notifications",
                "Updates about escalations, deals and your account.", markAll));

        list.addClassName("comment-list");
        add(list);
        refresh();
    }

    private void refresh() {
        list.removeAll();
        try {
            List<Notification> items = notificationService.list(userId);
            if (items.isEmpty()) {
                list.add(Ui.emptyState(VaadinIcon.BELL, "All caught up",
                        "New notifications will appear here."));
                return;
            }
            for (Notification notification : items) {
                list.add(buildRow(notification));
            }
        } catch (Exception e) {
            Ui.error(OrganizationListView.messageOf(e));
        }
    }

    private Div buildRow(Notification notification) {
        Div row = new Div();
        row.addClassName("comment");
        boolean unread = notification.getReadAt() == null;
        if (unread) {
            row.addClassName("unread");
            row.getStyle().set("border-left", "4px solid var(--primary)");
        }

        Div meta = new Div();
        meta.addClassName("comment-meta");
        Span type = new Span(notification.getType() == null ? "" : notification.getType());
        Span when = new Span(Ui.relative(notification.getCreatedAt()));
        when.addClassName("small");
        when.addClassName("muted");
        meta.add(type, when);

        Span title = new Span(notification.getTitle() == null ? "" : notification.getTitle());
        title.addClassName("list-row-title");
        Paragraph message = new Paragraph(notification.getMessage() == null ? "" : notification.getMessage());
        message.addClassName("small");

        row.add(meta, title, message);

        HorizontalLayout actions = new HorizontalLayout();
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        actions.setSpacing(true);
        if (notification.getLink() != null && !notification.getLink().isBlank()) {
            Button open = Ui.ghost("Open");
            open.addClickListener(event -> UI.getCurrent().navigate(notification.getLink()));
            actions.add(open);
        }
        if (unread) {
            Button markRead = Ui.ghost("Mark read");
            markRead.addClickListener(event -> {
                try {
                    notificationService.markRead(notification.getId(), userId);
                    refresh();
                } catch (Exception e) {
                    Ui.error(OrganizationListView.messageOf(e));
                }
            });
            actions.add(markRead);
        }
        if (actions.getComponentCount() > 0) {
            row.add(actions);
        }
        return row;
    }
}
