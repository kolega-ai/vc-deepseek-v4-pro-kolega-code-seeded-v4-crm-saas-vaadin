package com.crmops.channel.security;

import com.crmops.channel.domain.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which roles may enter a Vaadin route. Enforced by the
 * VaadinServiceInitListener access checker on EVERY navigation — hiding a link
 * in the menu is presentation only, this annotation is the guard.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RouteAccess {

    Role[] value();
}
