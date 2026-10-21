package com.crmops.channel.ui;

import com.crmops.channel.security.RouteAccess;
import com.crmops.channel.security.SecurityUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** Root route: forwards each role to its own dashboard. */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard · CrmOps")
@RouteAccess({com.crmops.channel.domain.Role.PLATFORM_ADMIN, com.crmops.channel.domain.Role.ACCOUNT_MANAGER,
        com.crmops.channel.domain.Role.SUPPORT_AGENT, com.crmops.channel.domain.Role.CUSTOMER_CONTACT})
public class DashboardView extends Div implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var principal = SecurityUtils.requireUser();
        Class<? extends Component> target = switch (principal.role()) {
            case PLATFORM_ADMIN -> AdminDashboardView.class;
            case ACCOUNT_MANAGER -> ManagerDashboardView.class;
            case SUPPORT_AGENT -> SupportDashboardView.class;
            case CUSTOMER_CONTACT -> CustomerDashboardView.class;
        };
        event.forwardTo(target);
    }
}
