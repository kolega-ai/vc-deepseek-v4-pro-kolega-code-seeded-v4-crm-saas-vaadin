package com.crmops.channel.web;

import com.crmops.channel.domain.ApiKey;
import com.crmops.channel.domain.CommentVisibility;
import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Escalation;
import com.crmops.channel.domain.EscalationComment;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.domain.Severity;
import com.crmops.channel.security.ApiKeyPrincipal;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.service.DocumentService;
import com.crmops.channel.service.EscalationService;
import com.crmops.channel.service.ImportExportService;
import com.crmops.channel.service.NotFoundException;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.ValidationException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.crmops.common.core.Actions.CONTACT_READ;
import static com.crmops.common.core.Actions.DOCUMENT_DOWNLOAD;
import static com.crmops.common.core.Actions.DOCUMENT_READ;
import static com.crmops.common.core.Actions.ESCALATION_CREATE;
import static com.crmops.common.core.Actions.ESCALATION_READ;
import static com.crmops.common.core.Actions.ESCALATION_UPDATE;
import static com.crmops.common.core.Actions.ORG_READ;

/**
 * Organization-scoped JSON API for customer integrations, authenticated with
 * API keys (X-Api-Key). Every endpoint re-authorizes through the decision tier
 * with the key's hash; the key's organization is the ONLY scope visible here.
 */
@RestController
@RequestMapping("/api/v1")
public class ApiV1Controller {

    private final CrmAuthorization authz;
    private final OrganizationService orgService;
    private final EscalationService escalationService;
    private final DocumentService documentService;
    private final ImportExportService importExportService;

    public ApiV1Controller(CrmAuthorization authz, OrganizationService orgService,
                           EscalationService escalationService, DocumentService documentService,
                           ImportExportService importExportService) {
        this.authz = authz;
        this.orgService = orgService;
        this.escalationService = escalationService;
        this.documentService = documentService;
        this.importExportService = importExportService;
    }

    @GetMapping("/org")
    public Map<String, Object> orgProfile(Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(ORG_READ, key.orgId());
        Organization org = orgService.get(key.orgId());
        // Customer-visible profile only: no health score, owner, MRR or internal notes.
        return Map.of(
                "id", org.getId(),
                "name", org.getName(),
                "plan", org.getPlan().getCode(),
                "status", org.getStatus().name(),
                "seats", org.getSeats(),
                "renewalDate", org.getRenewalDate() == null ? "" : org.getRenewalDate().toString(),
                "industry", org.getIndustry() == null ? "" : org.getIndustry(),
                "country", org.getCountry() == null ? "" : org.getCountry(),
                "website", org.getWebsite() == null ? "" : org.getWebsite());
    }

    @GetMapping("/escalations")
    public List<Map<String, Object>> escalations(Authentication authentication,
                                                 @RequestParam(required = false) String status) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(ESCALATION_READ, key.orgId());
        return escalationService.listByOrg(key.orgId()).stream()
                .filter(e -> status == null || e.getStatus().name().equalsIgnoreCase(status))
                .map(e -> Map.<String, Object>of(
                        "id", e.getId(),
                        "subject", e.getSubject(),
                        "description", e.getDescription(),
                        "severity", e.getSeverity().name(),
                        "status", e.getStatus().name(),
                        "createdAt", e.getCreatedAt().toString(),
                        "updatedAt", e.getUpdatedAt().toString()))
                .toList();
    }

    @GetMapping("/escalations/{id}")
    public Map<String, Object> escalationDetail(@PathVariable long id, Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        Escalation escalation;
        try {
            escalation = escalationService.get(id);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw new NotFoundException("escalation", id); // no cross-org enumeration
        }
        authz.require(ESCALATION_READ, key.orgId());
        if (!escalation.getOrg().getId().equals(key.orgId())) {
            throw new NotFoundException("escalation", id);
        }
        List<Map<String, Object>> comments = escalationService.comments(id).stream()
                .filter(c -> CommentVisibility.CUSTOMER_VISIBLE.name().equals(c.visibility()))
                .map(c -> Map.<String, Object>of("body", c.body(), "createdAt", c.createdAt().toString()))
                .toList();
        return Map.of(
                "id", escalation.getId(),
                "subject", escalation.getSubject(),
                "description", escalation.getDescription(),
                "severity", escalation.getSeverity().name(),
                "status", escalation.getStatus().name(),
                "createdAt", escalation.getCreatedAt().toString(),
                "comments", comments);
    }

    public record NewEscalation(String subject, String description, String severity) {
    }

    @PostMapping("/escalations")
    public Map<String, Object> createEscalation(@RequestBody NewEscalation body, Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(ESCALATION_CREATE, key.orgId());
        Severity severity;
        try {
            severity = body.severity() == null ? Severity.SEV3 : Severity.valueOf(body.severity().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("severity must be SEV1, SEV2 or SEV3");
        }
        Escalation created = escalationService.create(key.orgId(), body.subject(), body.description(), severity);
        return Map.of("id", created.getId(), "status", created.getStatus().name(),
                "createdAt", created.getCreatedAt().toString());
    }

    public record Reply(String body) {
    }

    @PostMapping("/escalations/{id}/reply")
    public Map<String, Object> reply(@PathVariable long id, @RequestBody Reply body, Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        Escalation escalation;
        try {
            escalation = escalationService.get(id);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw new NotFoundException("escalation", id); // no cross-org enumeration
        }
        authz.require(ESCALATION_UPDATE, key.orgId());
        if (!escalation.getOrg().getId().equals(key.orgId())) {
            throw new NotFoundException("escalation", id);
        }
        EscalationComment comment = escalationService.addCustomerVisibleUpdate(id, body.body());
        return Map.of("commentId", comment.getId(), "createdAt", comment.getCreatedAt().toString());
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> documents(Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(DOCUMENT_READ, key.orgId());
        return documentService.list(key.orgId()).stream() // list() filters to customer-visible
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "name", d.getName(),
                        "category", d.getCategory().name(),
                        "sizeBytes", d.getSizeBytes(),
                        "uploadedAt", d.getUploadedAt().toString()))
                .toList();
    }

    public record DocumentQuery(String category, String visibility) {
    }

    @PostMapping("/documents/search")
    public List<Map<String, Object>> searchDocuments(@RequestBody DocumentQuery body,
                                                     Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(DOCUMENT_READ, key.orgId());
        com.crmops.channel.domain.DocCategory category;
        com.crmops.channel.domain.DocVisibility visibility;
        try {
            category = body.category() == null || body.category().isBlank() ? null
                    : com.crmops.channel.domain.DocCategory.valueOf(body.category().toUpperCase());
            visibility = body.visibility() == null || body.visibility().isBlank() ? null
                    : com.crmops.channel.domain.DocVisibility.valueOf(body.visibility().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("unknown category or visibility filter");
        }
        return documentService.search(key.orgId(), category, visibility).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "name", d.getName(),
                        "category", d.getCategory().name(),
                        "visibility", d.getVisibility().name(),
                        "sizeBytes", d.getSizeBytes(),
                        "uploadedAt", d.getUploadedAt().toString()))
                .toList();
    }

    /** Serializable shape of a saved escalation queue filter carried by the desk connector. */
    public static class EscQueue implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private String status;
        private String severity;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }
    }

    /** Reopens the queue filter a desk connector restored from its last session. */
    @GetMapping("/escalations/queue-view")
    public List<Map<String, Object>> queueView(@RequestHeader("X-CrmOps-Queue") String blob,
                                               Authentication authentication) throws Exception {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(ESCALATION_READ, key.orgId());
        byte[] raw = Base64.getUrlDecoder().decode(blob);
        EscQueue q = (EscQueue) new ObjectInputStream(new ByteArrayInputStream(raw)).readObject();
        String status = q.getStatus();
        String severity = q.getSeverity();
        return escalationService.listByOrg(key.orgId()).stream()
                .filter(e -> status == null || e.getStatus().name().equalsIgnoreCase(status))
                .filter(e -> severity == null || e.getSeverity().name().equalsIgnoreCase(severity))
                .map(e -> Map.<String, Object>of(
                        "id", e.getId(),
                        "subject", e.getSubject(),
                        "severity", e.getSeverity().name(),
                        "status", e.getStatus().name()))
                .toList();
    }

    /** Partner pre-flight: which contacts from an uploaded reference list already exist. */
    @PostMapping("/contacts/match")
    public List<Map<String, Object>> matchContacts(@RequestParam("refs") MultipartFile file,
                                                   Authentication authentication) throws java.io.IOException {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(CONTACT_READ, key.orgId());
        return importExportService.matchContacts(key.orgId(), file.getBytes());
    }

    /** Serializable shape of a document filter shared between integration clients. */
    public static class SavedView implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private String category;
        private String visibility;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getVisibility() {
            return visibility;
        }

        public void setVisibility(String visibility) {
            this.visibility = visibility;
        }
    }

    /** Restores a saved document filter previously shared between teammates. */
    @GetMapping("/documents/saved")
    public List<Map<String, Object>> savedView(@RequestParam("view") String view, Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(DOCUMENT_READ, key.orgId());
        SavedView saved = (SavedView) new com.thoughtworks.xstream.XStream().fromXML(view);
        com.crmops.channel.domain.DocCategory category;
        com.crmops.channel.domain.DocVisibility visibility;
        try {
            category = saved.getCategory() == null || saved.getCategory().isBlank() ? null
                    : com.crmops.channel.domain.DocCategory.valueOf(saved.getCategory().toUpperCase());
            visibility = saved.getVisibility() == null || saved.getVisibility().isBlank() ? null
                    : com.crmops.channel.domain.DocVisibility.valueOf(saved.getVisibility().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("unknown category or visibility filter");
        }
        return documentService.search(key.orgId(), category, visibility).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "name", d.getName(),
                        "category", d.getCategory().name(),
                        "visibility", d.getVisibility().name(),
                        "sizeBytes", d.getSizeBytes(),
                        "uploadedAt", d.getUploadedAt().toString()))
                .toList();
    }

    @GetMapping("/documents/{id}/content")
    public ResponseEntity<InputStreamResource> documentContent(@PathVariable long id, Authentication authentication) {
        ApiKeyPrincipal key = (ApiKeyPrincipal) authentication.getPrincipal();
        authz.require(DOCUMENT_DOWNLOAD, key.orgId());
        DocumentService.Download download;
        try {
            download = documentService.downloadStreamFor(id,
                    new com.crmops.channel.security.CrmPrincipal(null, "api-key", key.keyName(), null,
                            com.crmops.channel.domain.Role.CUSTOMER_CONTACT, key.orgId(), null));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw new NotFoundException("document", id); // no cross-org enumeration
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .body(new InputStreamResource(download.stream()));
    }
}
