package com.crmops.common.core;

/**
 * Canonical authorization action names. The {@code decision} tier evaluates
 * these actions against the acting user's role and organization scope. The
 * {@code channel} tier must call decision for every one of these actions
 * before executing the corresponding operation.
 */
public final class Actions {

    // Organization
    public static final String ORG_READ = "org.read";
    public static final String ORG_CREATE = "org.create";
    public static final String ORG_UPDATE = "org.update";
    public static final String ORG_PLAN_CHANGE = "org.plan_change";
    public static final String ORG_STATUS_CHANGE = "org.status_change";

    // Contacts
    public static final String CONTACT_READ = "contact.read";
    public static final String CONTACT_CREATE = "contact.create";
    public static final String CONTACT_UPDATE = "contact.update";
    public static final String CONTACT_INVITE = "contact.invite";

    // Deals & renewals
    public static final String DEAL_READ = "deal.read";
    public static final String DEAL_CREATE = "deal.create";
    public static final String DEAL_UPDATE = "deal.update";
    public static final String DEAL_STAGE_CHANGE = "deal.stage_change";

    // Escalations
    public static final String ESCALATION_READ = "escalation.read";
    public static final String ESCALATION_CREATE = "escalation.create";
    public static final String ESCALATION_ASSIGN = "escalation.assign";
    public static final String ESCALATION_UPDATE = "escalation.update";
    public static final String ESCALATION_INTERNAL_NOTE = "escalation.internal_note";

    // Documents
    public static final String DOCUMENT_READ = "document.read";
    public static final String DOCUMENT_UPLOAD = "document.upload";
    public static final String DOCUMENT_DOWNLOAD = "document.download";
    public static final String DOCUMENT_MANAGE = "document.manage";

    // API keys & webhooks
    public static final String API_KEY_READ = "api_key.read";
    public static final String API_KEY_CREATE = "api_key.create";
    public static final String API_KEY_ROTATE = "api_key.rotate";
    public static final String API_KEY_REVOKE = "api_key.revoke";
    public static final String WEBHOOK_READ = "webhook.read";
    public static final String WEBHOOK_MANAGE = "webhook.manage";
    public static final String WEBHOOK_TEST = "webhook.test";

    // Billing (served by core-mock behind a decision-minted scope token)
    public static final String BILLING_READ = "billing.read";

    // Platform
    public static final String USER_ADMIN = "user.admin";
    public static final String AUDIT_READ = "audit.read";
    public static final String IMPORT_RUN = "import.run";
    public static final String EXPORT_RUN = "export.run";
    public static final String OUTBOX_READ = "outbox.read";
    public static final String NOTIFICATION_READ = "notification.read";
    public static final String PLAN_READ = "plan.read";

    private Actions() {
    }
}
