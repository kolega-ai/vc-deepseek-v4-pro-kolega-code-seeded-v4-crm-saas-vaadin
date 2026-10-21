package com.crmops.channel.service;

import com.crmops.channel.domain.DocVisibility;
import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.ActingPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import static com.crmops.common.core.Actions.DOCUMENT_DOWNLOAD;
import static com.crmops.common.core.Actions.DOCUMENT_READ;

/**
 * Shared document access policy. This is one of the application's canonical
 * precision-baseline locations: the ownership/visibility check runs in this
 * shared service, invoked one call frame ABOVE the view/controller method
 * (callers like {@link DocumentService#downloadStream} delegate here rather
 * than inlining the check). Visibility is enforced by role:
 * <ul>
 *   <li>customers: only their own org's CUSTOMER_VISIBLE documents</li>
 *   <li>support agents: org-accessible documents, but never BILLING_ONLY</li>
 *   <li>account managers / platform admins: all documents of orgs they can access</li>
 * </ul>
 * Organization access itself is enforced by the decision tier through
 * {@link CrmAuthorization}; this service adds the per-visibility-level rules.
 */
@Service
public class DocumentAccessService {

    private final CrmAuthorization authz;

    public DocumentAccessService(CrmAuthorization authz) {
        this.authz = authz;
    }

    public void assertCanRead(ActingPrincipal principal, DocumentMeta doc) {
        authz.require(DOCUMENT_READ, doc.getOrg().getId());
        if (!visibilityAllows(principal, doc)) {
            throw new AccessDeniedException("this document is not visible to your role");
        }
    }

    public void assertCanDownload(ActingPrincipal principal, DocumentMeta doc) {
        assertCanRead(principal, doc);
        authz.require(DOCUMENT_DOWNLOAD, doc.getOrg().getId());
    }

    /** Visibility-level rules shared by list filtering and per-document assertions. */
    public static boolean visibilityAllows(ActingPrincipal principal, DocumentMeta doc) {
        if (principal == null) {
            return false;
        }
        DocVisibility visibility = doc.getVisibility();
        return switch (principal.role()) {
            case CUSTOMER_CONTACT -> visibility == DocVisibility.CUSTOMER_VISIBLE;
            case SUPPORT_AGENT -> visibility != DocVisibility.BILLING_ONLY;
            case ACCOUNT_MANAGER, PLATFORM_ADMIN -> true;
        };
    }
}
