package com.crmops.channel.service;

import com.crmops.channel.domain.DocCategory;
import com.crmops.channel.domain.DocVisibility;
import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Role;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.CrmPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Visibility rules of the shared document access policy (one of the canonical
 * precision-baseline locations: the check lives in a shared service invoked
 * one call frame above the view/controller).
 */
class DocumentAccessServiceTest {

    private DocumentAccessService service;

    @BeforeEach
    void setUp() {
        CrmAuthorization authz = Mockito.mock(CrmAuthorization.class);
        // require() is a no-op here: these tests exercise the visibility layer.
        Mockito.when(authz.require(Mockito.anyString(), Mockito.any())).thenReturn(null);
        service = new DocumentAccessService(authz);
    }

    private DocumentMeta doc(DocVisibility visibility, long orgId) {
        Organization org = new Organization("Org", null, null, null, null, 70, null, null, null,
                null, 10, null, null, Instant.now());
        return new DocumentMeta(org, "file.pdf", "stored.pdf", DocCategory.CONTRACT, visibility,
                "application/pdf", 10, null, null, Instant.now()) {
            @Override
            public Organization getOrg() {
                return orgWithId(orgId);
            }
        };
    }

    private Organization orgWithId(long id) {
        Organization org = new Organization("Org", null, null, null, null, 70, null, null, null,
                null, 10, null, null, Instant.now());
        try {
            var field = Organization.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(org, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return org;
    }

    private CrmPrincipal principal(Role role, Long customerOrgId) {
        return new CrmPrincipal(1L, "u", "User", "u@example.com", role, customerOrgId, "token");
    }

    @Test
    void customersOnlySeeCustomerVisibleDocuments() {
        assertTrue(DocumentAccessService.visibilityAllows(principal(Role.CUSTOMER_CONTACT, 1L),
                doc(DocVisibility.CUSTOMER_VISIBLE, 1L)));
        assertFalse(DocumentAccessService.visibilityAllows(principal(Role.CUSTOMER_CONTACT, 1L),
                doc(DocVisibility.INTERNAL, 1L)));
        assertFalse(DocumentAccessService.visibilityAllows(principal(Role.CUSTOMER_CONTACT, 1L),
                doc(DocVisibility.BILLING_ONLY, 1L)));
    }

    @Test
    void supportAgentsNeverSeeBillingOnlyDocuments() {
        assertTrue(DocumentAccessService.visibilityAllows(principal(Role.SUPPORT_AGENT, null),
                doc(DocVisibility.INTERNAL, 1L)));
        assertTrue(DocumentAccessService.visibilityAllows(principal(Role.SUPPORT_AGENT, null),
                doc(DocVisibility.CUSTOMER_VISIBLE, 1L)));
        assertFalse(DocumentAccessService.visibilityAllows(principal(Role.SUPPORT_AGENT, null),
                doc(DocVisibility.BILLING_ONLY, 1L)));
    }

    @Test
    void managersAndAdminsSeeEverything() {
        assertTrue(DocumentAccessService.visibilityAllows(principal(Role.ACCOUNT_MANAGER, null),
                doc(DocVisibility.BILLING_ONLY, 1L)));
        assertTrue(DocumentAccessService.visibilityAllows(principal(Role.PLATFORM_ADMIN, null),
                doc(DocVisibility.INTERNAL, 1L)));
    }
}
