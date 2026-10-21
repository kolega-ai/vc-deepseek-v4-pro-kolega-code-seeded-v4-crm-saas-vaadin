package com.crmops.decision.policy;

import com.crmops.common.core.Actions;
import com.crmops.common.core.Roles;

import java.util.Map;
import java.util.Set;

/**
 * Static role-to-action policy matrix. Roles are fixed for this product;
 * organization scope (which orgs a given user may touch) is data, held in
 * {@code org_assignments} and evaluated on every request.
 */
public final class Policy {

    private static final Set<String> STAFF_COMMON = Set.of(
            Actions.NOTIFICATION_READ, Actions.PLAN_READ);

    private static final Set<String> ADMIN_ACTIONS = Set.of(
            Actions.ORG_READ, Actions.ORG_CREATE, Actions.ORG_UPDATE, Actions.ORG_PLAN_CHANGE, Actions.ORG_STATUS_CHANGE,
            Actions.CONTACT_READ, Actions.CONTACT_CREATE, Actions.CONTACT_UPDATE, Actions.CONTACT_INVITE,
            Actions.DEAL_READ, Actions.DEAL_CREATE, Actions.DEAL_UPDATE, Actions.DEAL_STAGE_CHANGE,
            Actions.ESCALATION_READ, Actions.ESCALATION_CREATE, Actions.ESCALATION_ASSIGN,
            Actions.ESCALATION_UPDATE, Actions.ESCALATION_INTERNAL_NOTE,
            Actions.DOCUMENT_READ, Actions.DOCUMENT_UPLOAD, Actions.DOCUMENT_DOWNLOAD, Actions.DOCUMENT_MANAGE,
            Actions.API_KEY_READ, Actions.API_KEY_CREATE, Actions.API_KEY_ROTATE, Actions.API_KEY_REVOKE,
            Actions.WEBHOOK_READ, Actions.WEBHOOK_MANAGE, Actions.WEBHOOK_TEST,
            Actions.BILLING_READ,
            Actions.USER_ADMIN, Actions.AUDIT_READ, Actions.IMPORT_RUN, Actions.EXPORT_RUN,
            Actions.OUTBOX_READ, Actions.NOTIFICATION_READ, Actions.PLAN_READ);

    private static final Set<String> ACCOUNT_MANAGER_ACTIONS = union(STAFF_COMMON, Set.of(
            Actions.ORG_READ, Actions.ORG_CREATE, Actions.ORG_UPDATE,
            Actions.CONTACT_READ, Actions.CONTACT_CREATE, Actions.CONTACT_UPDATE, Actions.CONTACT_INVITE,
            Actions.DEAL_READ, Actions.DEAL_CREATE, Actions.DEAL_UPDATE, Actions.DEAL_STAGE_CHANGE,
            Actions.ESCALATION_READ,
            Actions.DOCUMENT_READ, Actions.DOCUMENT_UPLOAD, Actions.DOCUMENT_DOWNLOAD, Actions.DOCUMENT_MANAGE,
            Actions.API_KEY_READ,
            Actions.WEBHOOK_READ, Actions.WEBHOOK_MANAGE, Actions.WEBHOOK_TEST,
            Actions.BILLING_READ,
            Actions.IMPORT_RUN, Actions.EXPORT_RUN));

    private static final Set<String> SUPPORT_AGENT_ACTIONS = union(STAFF_COMMON, Set.of(
            Actions.ORG_READ, Actions.CONTACT_READ,
            Actions.ESCALATION_READ, Actions.ESCALATION_CREATE, Actions.ESCALATION_ASSIGN,
            Actions.ESCALATION_UPDATE, Actions.ESCALATION_INTERNAL_NOTE,
            Actions.DOCUMENT_READ, Actions.DOCUMENT_DOWNLOAD,
            Actions.EXPORT_RUN));

    private static final Set<String> CUSTOMER_ACTIONS = Set.of(
            Actions.ORG_READ,
            Actions.CONTACT_READ, Actions.CONTACT_CREATE, Actions.CONTACT_UPDATE,
            Actions.ESCALATION_READ, Actions.ESCALATION_CREATE, Actions.ESCALATION_UPDATE,
            Actions.DOCUMENT_READ, Actions.DOCUMENT_DOWNLOAD,
            Actions.NOTIFICATION_READ);

    /**
     * Actions available to API keys. API keys are always scoped to exactly one
     * organization and carry customer-contact level entitlements.
     */
    public static final Set<String> API_KEY_ACTIONS = Set.of(
            Actions.ORG_READ,
            Actions.CONTACT_READ,
            Actions.ESCALATION_READ, Actions.ESCALATION_CREATE, Actions.ESCALATION_UPDATE,
            Actions.DOCUMENT_READ, Actions.DOCUMENT_DOWNLOAD);

    private static final Map<String, Set<String>> ROLE_ACTIONS = Map.of(
            Roles.PLATFORM_ADMIN, ADMIN_ACTIONS,
            Roles.ACCOUNT_MANAGER, ACCOUNT_MANAGER_ACTIONS,
            Roles.SUPPORT_AGENT, SUPPORT_AGENT_ACTIONS,
            Roles.CUSTOMER_CONTACT, CUSTOMER_ACTIONS);

    /** Actions a support agent may perform on escalations regardless of org assignment (team queue). */
    private static final Set<String> TEAM_QUEUE_ACTIONS = Set.of(
            Actions.ESCALATION_READ, Actions.ESCALATION_CREATE, Actions.ESCALATION_ASSIGN,
            Actions.ESCALATION_UPDATE, Actions.ESCALATION_INTERNAL_NOTE);

    public static Set<String> allowedActions(String role) {
        return ROLE_ACTIONS.getOrDefault(role, Set.of());
    }

    public static boolean bypassesOrgScope(String role, String action) {
        return Roles.SUPPORT_AGENT.equals(role) && TEAM_QUEUE_ACTIONS.contains(action);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Policy() {
    }
}
