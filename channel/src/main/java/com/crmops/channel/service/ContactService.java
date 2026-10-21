package com.crmops.channel.service;

import com.crmops.channel.domain.Contact;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.repo.ContactRepository;
import com.crmops.channel.security.CrmAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.crmops.common.core.Actions.CONTACT_CREATE;
import static com.crmops.common.core.Actions.CONTACT_READ;
import static com.crmops.common.core.Actions.CONTACT_UPDATE;

/** Contact management, scoped to the acting user's organizations. */
@Service
public class ContactService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ContactRepository contactRepo;
    private final OrganizationService orgService;
    private final CrmAuthorization authz;
    private final AuditService audit;

    public ContactService(ContactRepository contactRepo, OrganizationService orgService,
                          CrmAuthorization authz, AuditService audit) {
        this.contactRepo = contactRepo;
        this.orgService = orgService;
        this.authz = authz;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Contact> list(long orgId) {
        authz.require(CONTACT_READ, orgId);
        return contactRepo.findByOrgIdOrderByPrimaryDescLastNameAsc(orgId);
    }

    @Transactional(readOnly = true)
    public Contact get(long contactId) {
        Contact contact = contactRepo.findById(contactId).orElseThrow(() -> new NotFoundException("contact", contactId));
        authz.require(CONTACT_READ, contact.getOrg().getId());
        return contact;
    }

    @Transactional
    public Contact create(long orgId, String firstName, String lastName, String email, String phone,
                          String title, String permissions, boolean billing, boolean primary) {
        var verdict = authz.require(CONTACT_CREATE, orgId);
        validate(firstName, lastName, email);
        Organization org = orgService.get(orgId);
        if (contactRepo.findByOrgIdAndEmailIgnoreCase(orgId, email.trim()).isPresent()) {
            throw new ValidationException("a contact with this email already exists in the organization");
        }
        Contact contact = new Contact(org, firstName.trim(), lastName.trim(), email.trim(), phone, title,
                permissions, billing, primary, null, Instant.now());
        contactRepo.save(contact);
        audit.record("contact.create", "contact", contact.getId(), orgId, null,
                Map.of("email", contact.getEmail(), "name", contact.fullName()),
                verdict.authorizationBasis(), false);
        return contact;
    }

    @Transactional
    public Contact update(long contactId, String firstName, String lastName, String phone, String title,
                          String permissions, boolean billing, boolean primary) {
        Contact contact = get(contactId);
        var verdict = authz.require(CONTACT_UPDATE, contact.getOrg().getId());
        validate(firstName, lastName, contact.getEmail());
        contact.update(firstName.trim(), lastName.trim(), phone, title, permissions, billing, primary, Instant.now());
        audit.record("contact.update", "contact", contactId, contact.getOrg().getId(), null,
                Map.of("name", contact.fullName(), "billing", billing, "primary", primary),
                verdict.authorizationBasis(), false);
        return contact;
    }

    /** Customer portal: customers may only edit their own contact record. */
    @Transactional
    public Contact updateOwnContact(long contactId, String phone, String title) {
        Contact contact = get(contactId);
        var verdict = authz.require(CONTACT_UPDATE, contact.getOrg().getId());
        var principal = com.crmops.channel.security.SecurityUtils.requireUser();
        if (!contact.getUserId().equals(principal.userId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "customers can only update their own contact record");
        }
        contact.update(contact.getFirstName(), contact.getLastName(), phone, title,
                contact.getPermissions(), contact.isBilling(), contact.isPrimary(), Instant.now());
        audit.record("contact.update_own", "contact", contactId, contact.getOrg().getId(), null,
                Map.of("phone", phone, "title", title), verdict.authorizationBasis(), false);
        return contact;
    }

    public static void validate(String firstName, String lastName, String email) {
        if (firstName == null || firstName.isBlank() || firstName.length() > 80) {
            throw new ValidationException("first name is required (max 80 characters)");
        }
        if (lastName == null || lastName.isBlank() || lastName.length() > 80) {
            throw new ValidationException("last name is required (max 80 characters)");
        }
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("a valid email address is required");
        }
    }
}
