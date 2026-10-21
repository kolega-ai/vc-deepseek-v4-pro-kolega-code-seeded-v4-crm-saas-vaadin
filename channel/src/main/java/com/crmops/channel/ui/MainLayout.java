package com.crmops.channel.ui;

import com.crmops.channel.domain.Role;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.AssetCacheBuster;
import com.crmops.channel.service.NotificationService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouterLink;

import java.util.ArrayList;
import java.util.List;

/**
 * The application shell: dark sidebar with role-aware navigation, top bar with
 * the current page title, the notification bell and the account menu.
 */
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final NotificationService notifications;
    private final Span unreadBadge = new Span("0");
    private final List<NavEntry> navEntries = new ArrayList<>();
    private final Span topbarTitle = new Span();
    private final Span topbarContext = new Span();

    public MainLayout(NotificationService notifications, AssetCacheBuster cacheBuster) {
        this.notifications = notifications;
        addClassName("app-shell");
        setPrimarySection(Section.DRAWER);

        addToDrawer(buildSidebar(cacheBuster));
        addToNavbar(buildTopbar());

        setDrawerOpened(false);
    }

    private VerticalLayout buildSidebar(AssetCacheBuster cacheBuster) {
        VerticalLayout sidebar = new VerticalLayout();
        sidebar.addClassName("app-sidebar");
        sidebar.setPadding(false);
        sidebar.setSpacing(false);
        sidebar.setSizeFull();

        Div inner = new Div();
        inner.addClassName("sidebar-inner");
        inner.setSizeFull();

        Div brand = new Div();
        brand.addClassName("brand");
        Image logo = new Image(cacheBuster.bust("themes/crmops/images/logo.svg"), "CrmOps logo");
        Div brandText = new Div();
        H4 name = new H4("CrmOps");
        name.addClassName("brand-name");
        Paragraph tag = new Paragraph("Customer Operations");
        tag.addClassName("brand-tag");
        brandText.add(name, tag);
        brand.add(logo, brandText);
        inner.add(brand);

        Role role = SecurityUtils.current() == null ? null : SecurityUtils.current().role();
        for (NavSection section : sectionsFor(role)) {
            Span label = new Span(section.label());
            label.addClassName("nav-section-label");
            inner.add(label);
            for (NavItem item : section.items()) {
                RouterLink link = new RouterLink("", item.routeClass());
                link.addClassName("nav-item");
                link.add(item.icon().create(), new Span(item.label()));
                link.getElement().setAttribute("aria-label", item.label());
                inner.add(link);
                navEntries.add(new NavEntry(link, item.routeClass()));
            }
        }

        Div spacer = new Div();
        spacer.addClassName("sidebar-spacer");
        inner.add(spacer);

        Div footer = new Div();
        footer.addClassName("sidebar-footer");
        footer.add(new Span("CrmOps v1.0 · local demo"));
        inner.add(footer);

        sidebar.add(inner);
        return sidebar;
    }

    private HorizontalLayout buildTopbar() {
        HorizontalLayout topbar = new HorizontalLayout();
        topbar.addClassName("app-topbar");
        topbar.setWidthFull();
        topbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        DrawerToggle toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Toggle navigation");
        topbar.add(toggle);

        Div titles = new Div();
        topbarTitle.addClassName("topbar-title");
        topbarContext.addClassName("topbar-context");
        titles.add(topbarTitle, topbarContext);
        topbar.add(titles);
        topbar.setFlexGrow(1, titles);

        Span bellWrap = new Span();
        bellWrap.addClassName("notification-bell");
        Button bell = new Button(VaadinIcon.BELL.create());
        bell.getElement().setAttribute("aria-label", "Notifications");
        bell.addClickListener(event -> UI.getCurrent().navigate(NotificationsView.class));
        unreadBadge.addClassName("notification-dot");
        unreadBadge.setVisible(false);
        bellWrap.add(bell, unreadBadge);
        topbar.add(bellWrap);

        topbar.add(buildAccountMenu());
        return topbar;
    }

    private MenuBar buildAccountMenu() {
        MenuBar menu = new MenuBar();
        String displayName = SecurityUtils.current() == null ? "Account" : SecurityUtils.current().displayName();
        String initials = initials(displayName);
        Span avatar = new Span(initials);
        avatar.addClassName("user-avatar");
        HorizontalLayout label = new HorizontalLayout(avatar, new Span(displayName));
        label.addClassName("topbar-user");
        label.setAlignItems(FlexComponent.Alignment.CENTER);
        label.setSpacing(true);

        menu.addItem(label, null, event -> {
        });
        menu.addItem("Profile", event -> UI.getCurrent().navigate(ProfileView.class));
        menu.addItem("Notifications", event -> UI.getCurrent().navigate(NotificationsView.class));
        menu.addItem("Sign out", event -> {
            UI.getCurrent().getSession().close();
            UI.getCurrent().getPage().setLocation("/login");
        });
        return menu;
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        Class<?> target = event.getActiveChain().isEmpty() ? null
                : event.getActiveChain().get(event.getActiveChain().size() - 1).getClass();
        String title = target != null && target.isAnnotationPresent(PageTitle.class)
                ? target.getAnnotation(PageTitle.class).value().split(" · ")[0]
                : "CrmOps";
        topbarTitle.setText(title);
        var principal = SecurityUtils.current();
        topbarContext.setText(principal == null ? "" : roleLabel(principal.role()));
        for (NavEntry entry : navEntries) {
            boolean active = target != null && entry.routeClass().equals(target);
            if (active) {
                entry.link().addClassName("active");
            } else {
                entry.link().removeClassName("active");
            }
        }
        if (principal != null && principal.userId() != null) {
            long unread = notifications.unreadCount(principal.userId());
            unreadBadge.setVisible(unread > 0);
            unreadBadge.setText(String.valueOf(Math.min(unread, 99)));
        }
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case PLATFORM_ADMIN -> "Platform administrator";
            case ACCOUNT_MANAGER -> "Account manager";
            case SUPPORT_AGENT -> "Support agent";
            case CUSTOMER_CONTACT -> "Customer portal";
        };
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0) {
            return "?";
        }
        return (parts[0].substring(0, 1) + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "")).toUpperCase();
    }

    private static List<NavSection> sectionsFor(Role role) {
        if (role == null) {
            return List.of();
        }
        if (role == Role.CUSTOMER_CONTACT) {
            return List.of(
                    new NavSection("Portal", List.of(
                            new NavItem("Home", VaadinIcon.HOME, DashboardView.class),
                            new NavItem("My requests", VaadinIcon.COMMENTS, PortalRequestsView.class),
                            new NavItem("Documents", VaadinIcon.FILE, PortalDocumentsView.class),
                            new NavItem("Contacts", VaadinIcon.USERS, PortalContactsView.class))));
        }
        List<NavItem> workspace = new ArrayList<>(List.of(
                new NavItem("Dashboard", VaadinIcon.HOME, DashboardView.class),
                new NavItem("Organizations", VaadinIcon.BUILDING, OrganizationListView.class),
                new NavItem("Deal pipeline", VaadinIcon.CHART, DealPipelineView.class),
                new NavItem("Escalation queue", VaadinIcon.COMMENTS_O, EscalationQueueView.class)));
        List<NavItem> admin = new ArrayList<>();
        if (role == Role.PLATFORM_ADMIN) {
            admin.add(new NavItem("Users", VaadinIcon.USER, UsersAdminView.class));
            admin.add(new NavItem("Plans", VaadinIcon.GRID, PlansView.class));
            admin.add(new NavItem("Audit log", VaadinIcon.CLIPBOARD_TEXT, AuditLogView.class));
            admin.add(new NavItem("Import & export", VaadinIcon.UPLOAD_ALT, ImportExportView.class));
            admin.add(new NavItem("Email outbox", VaadinIcon.ENVELOPE, OutboxView.class));
        }
        List<NavSection> sections = new ArrayList<>();
        sections.add(new NavSection("Workspace", workspace));
        if (!admin.isEmpty()) {
            sections.add(new NavSection("Administration", admin));
        }
        return sections;
    }

    private record NavItem(String label, VaadinIcon icon, Class<? extends com.vaadin.flow.component.Component> routeClass) {
    }

    private record NavSection(String label, List<NavItem> items) {
    }

    private record NavEntry(RouterLink link, Class<?> routeClass) {
    }
}
