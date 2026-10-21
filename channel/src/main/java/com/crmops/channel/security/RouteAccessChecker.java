package com.crmops.channel.security;

import com.crmops.channel.domain.Role;
import com.crmops.channel.ui.AccessDeniedView;
import com.crmops.channel.ui.LoginView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Guards every @Route navigation by the route's {@link RouteAccess} annotation.
 * This runs server-side on navigation: a caller can reach any URL, so the
 * check here (and the re-checks in every service) is the actual authorization,
 * not the menu visibility.
 */
@Component
public class RouteAccessChecker implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(this::guard));
    }

    private void guard(BeforeEnterEvent event) {
        Class<?> target = event.getNavigationTarget();
        RouteAccess access = target.getAnnotation(RouteAccess.class);
        if (access == null) {
            return; // public flow views (login, reset, invite, error)
        }
        ActingPrincipal principal = SecurityUtils.current();
        if (principal == null || principal.isSystem()) {
            event.rerouteTo(LoginView.class);
            return;
        }
        boolean allowed = Arrays.asList(access.value()).contains(principal.role());
        if (!allowed) {
            event.rerouteTo(AccessDeniedView.class);
        }
    }

    /** For use by UI code when a session went away mid-navigation. */
    public static void forceLogout() {
        SecurityContextHolderHolder.clear();
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.close();
        }
    }

    private static final class SecurityContextHolderHolder {
        static void clear() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
