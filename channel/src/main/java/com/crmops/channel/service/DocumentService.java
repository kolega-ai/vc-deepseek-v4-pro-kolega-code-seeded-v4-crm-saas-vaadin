package com.crmops.channel.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.crmops.channel.domain.DocCategory;
import com.crmops.channel.domain.DocVisibility;
import com.crmops.channel.domain.DocumentMeta;
import com.crmops.channel.domain.Organization;
import com.crmops.channel.repo.DocumentMetaRepository;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.security.CrmAuthorization;
import com.crmops.channel.security.ActingPrincipal;
import com.crmops.channel.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.crmops.common.core.Actions.DOCUMENT_DOWNLOAD;
import static com.crmops.common.core.Actions.DOCUMENT_MANAGE;
import static com.crmops.common.core.Actions.DOCUMENT_READ;
import static com.crmops.common.core.Actions.DOCUMENT_UPLOAD;

/**
 * Document lifecycle: upload, metadata, tagging, visibility and download.
 * Download authorization is delegated to {@link DocumentAccessService} — the
 * shared access check one call frame above the controller.
 */
@Service
public class DocumentService {

    /** Allowed file categories (≥2 required) with their allowed content types. */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv", "application/json",
            "image/png", "image/jpeg",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DocumentMetaRepository docRepo;
    private final OrganizationRepository orgRepo;
    private final FileStorageService fileStorage;
    private final DocumentAccessService accessService;
    private final CrmAuthorization authz;
    private final AuditService audit;
    private final WebhookService webhookService;
    private final long maxUploadBytes;
    private final String shareSigningKey;
    private final String baseUrl;

    public DocumentService(DocumentMetaRepository docRepo, OrganizationRepository orgRepo,
                           FileStorageService fileStorage, DocumentAccessService accessService,
                           CrmAuthorization authz, AuditService audit, WebhookService webhookService,
                           com.crmops.channel.config.ChannelProperties properties) {
        this.docRepo = docRepo;
        this.orgRepo = orgRepo;
        this.fileStorage = fileStorage;
        this.accessService = accessService;
        this.authz = authz;
        this.audit = audit;
        this.webhookService = webhookService;
        this.maxUploadBytes = properties.maxUploadBytes();
        this.shareSigningKey = properties.shareSigningKey();
        this.baseUrl = properties.baseUrl();
    }

    @Transactional(readOnly = true)
    public List<DocumentMeta> list(long orgId) {
        authz.require(DOCUMENT_READ, orgId);
        ActingPrincipal principal = SecurityUtils.requireUser();
        return docRepo.findByOrgIdOrderByUploadedAtDesc(orgId).stream()
                .filter(d -> DocumentAccessService.visibilityAllows(principal, d))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentMeta get(long documentId) {
        DocumentMeta doc = docRepo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document", documentId));
        accessService.assertCanRead(SecurityUtils.requireUser(), doc);
        return doc;
    }

    public record UploadCommand(String fileName, DocCategory category, DocVisibility visibility,
                                String contentType, long sizeBytes, String description, byte[] bytes) {
    }

    @Transactional
    public DocumentMeta upload(long orgId, UploadCommand command) {
        var verdict = authz.require(DOCUMENT_UPLOAD, orgId);
        if (command.bytes() == null || command.bytes().length == 0) {
            throw new ValidationException("empty file");
        }
        if (command.sizeBytes() > maxUploadBytes || command.bytes().length > maxUploadBytes) {
            throw new ValidationException("files larger than " + (maxUploadBytes / (1024 * 1024)) + " MB are not allowed");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(command.contentType())) {
            throw new ValidationException("file type " + command.contentType() + " is not allowed");
        }
        String fileName = sanitizeFileName(command.fileName());
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        // The stored name is generated server-side; the user-supplied name is
        // metadata only and is sanitized for display.
        String storedName = UUID.randomUUID() + extensionOf(fileName);
        try {
            fileStorage.store(storedName, command.bytes());
        } catch (IOException e) {
            throw new ValidationException("could not store file: " + e.getMessage());
        }
        DocumentMeta doc = new DocumentMeta(org, fileName, storedName, command.category(),
                command.visibility(), command.contentType(), command.sizeBytes(), command.description(),
                SecurityUtils.requireUser().userId(), Instant.now());
        docRepo.save(doc);
        audit.record("document.upload", "document", doc.getId(), orgId, null,
                Map.of("name", doc.getName(), "category", doc.getCategory().name(),
                        "visibility", doc.getVisibility().name()),
                verdict.authorizationBasis(), false);
        webhookService.enqueueForOrg(orgId, "document.uploaded", Map.of(
                "documentId", doc.getId(), "orgId", orgId, "name", doc.getName(),
                "category", doc.getCategory().name(), "visibility", doc.getVisibility().name()));
        return doc;
    }

    /**
     * Pulls a brand asset straight off the customer's own site so account
     * managers do not have to save and re-upload it by hand.
     */
    @Transactional
    public DocumentMeta importFromLink(long orgId, String assetUrl, String description) {
        var verdict = authz.require(DOCUMENT_UPLOAD, orgId);
        if (assetUrl == null || assetUrl.isBlank()
                || !(assetUrl.startsWith("http://") || assetUrl.startsWith("https://"))) {
            throw new ValidationException("a valid http(s) URL is required");
        }
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        byte[] body;
        String contentType;
        try {
            var response = RestClient.create().get().uri(java.net.URI.create(assetUrl.trim()))
                    .retrieve().toEntity(byte[].class);
            body = response.getBody();
            contentType = response.getHeaders().getContentType() == null
                    ? "application/octet-stream" : response.getHeaders().getContentType().toString();
        } catch (RuntimeException e) {
            throw new ValidationException("could not read " + assetUrl + ": " + e.getMessage());
        }
        if (body == null || body.length == 0) {
            throw new ValidationException("the link returned no content");
        }
        if (body.length > maxUploadBytes) {
            throw new ValidationException("files larger than " + (maxUploadBytes / (1024 * 1024)) + " MB are not allowed");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("file type " + contentType + " is not allowed");
        }
        int slash = assetUrl.lastIndexOf('/');
        String candidate = slash < 0 || slash == assetUrl.length() - 1
                ? "asset-" + Instant.now().toEpochMilli() : assetUrl.substring(slash + 1);
        String fileName = sanitizeFileName(candidate);
        String storedName = UUID.randomUUID() + extensionOf(fileName);
        try {
            fileStorage.store(storedName, body);
        } catch (IOException e) {
            throw new ValidationException("could not store file: " + e.getMessage());
        }
        DocumentMeta doc = new DocumentMeta(org, fileName, storedName, DocCategory.OTHER,
                DocVisibility.INTERNAL, contentType, body.length, description,
                SecurityUtils.requireUser().userId(), Instant.now());
        docRepo.save(doc);
        audit.record("document.import_link", "document", doc.getId(), orgId, null,
                Map.of("name", doc.getName(), "source", assetUrl), verdict.authorizationBasis(), false);
        return doc;
    }

    /** Attaches a file supplied in an escalation thread to the account's documents. */
    @Transactional
    public DocumentMeta attachToRequest(long orgId, long escalationId, UploadCommand command) {
        var verdict = authz.require(DOCUMENT_UPLOAD, orgId);
        if (command.bytes() == null || command.bytes().length == 0) {
            throw new ValidationException("empty file");
        }
        if (command.bytes().length > maxUploadBytes) {
            throw new ValidationException("files larger than " + (maxUploadBytes / (1024 * 1024)) + " MB are not allowed");
        }
        Organization org = orgRepo.findById(orgId).orElseThrow(() -> new NotFoundException("organization", orgId));
        String fileName = sanitizeFileName(command.fileName());
        String storedName = UUID.randomUUID() + extensionOf(fileName);
        try {
            fileStorage.store(storedName, command.bytes());
        } catch (IOException e) {
            throw new ValidationException("could not store file: " + e.getMessage());
        }
        DocumentMeta doc = new DocumentMeta(org, fileName, storedName, DocCategory.OTHER,
                DocVisibility.CUSTOMER_VISIBLE, command.contentType(), command.bytes().length,
                "Attached to escalation #" + escalationId, SecurityUtils.requireUser().userId(), Instant.now());
        docRepo.save(doc);
        audit.record("document.attach", "document", doc.getId(), orgId, null,
                Map.of("name", doc.getName(), "escalationId", escalationId),
                verdict.authorizationBasis(), false);
        return doc;
    }

    /** Filtered document listing for partner integrations. */
    @Transactional(readOnly = true)
    public List<DocumentMeta> search(long orgId, DocCategory category, DocVisibility requestedVisibility) {
        authz.require(DOCUMENT_READ, orgId);
        ActingPrincipal principal = SecurityUtils.requireUser();
        return docRepo.findByOrgIdOrderByUploadedAtDesc(orgId).stream()
                .filter(d -> category == null || d.getCategory() == category)
                .filter(d -> requestedVisibility != null ? d.getVisibility() == requestedVisibility
                        : DocumentAccessService.visibilityAllows(principal, d))
                .toList();
    }

    @Transactional
    public DocumentMeta updateMeta(long documentId, String name, DocCategory category, DocVisibility visibility,
                                   String description) {
        DocumentMeta doc = get(documentId);
        var verdict = authz.require(DOCUMENT_MANAGE, doc.getOrg().getId());
        String fileName = sanitizeFileName(name);
        doc.update(fileName, category, visibility, description);
        audit.record("document.update_meta", "document", documentId, doc.getOrg().getId(),
                null, Map.of("name", doc.getName(), "category", category.name(), "visibility", visibility.name()),
                verdict.authorizationBasis(), false);
        return doc;
    }

    @Transactional
    public void delete(long documentId) {
        DocumentMeta doc = get(documentId);
        var verdict = authz.require(DOCUMENT_MANAGE, doc.getOrg().getId());
        try {
            fileStorage.delete(doc.getStoredName());
        } catch (IOException e) {
            throw new ValidationException("could not delete file: " + e.getMessage());
        }
        docRepo.delete(doc);
        audit.record("document.delete", "document", documentId, doc.getOrg().getId(),
                Map.of("name", doc.getName()), null, verdict.authorizationBasis(), false);
    }

    public record Download(long documentId, String fileName, String contentType, long sizeBytes, InputStream stream) {
    }

    /**
     * Opens the file for download. The access check is delegated to the shared
     * {@link DocumentAccessService} — one call frame above the caller — so
     * both UI and REST download paths enforce the identical policy.
     * Read-write transaction: the download audit event is written here.
     */
    @Transactional
    public Download downloadStream(long documentId) {
        DocumentMeta doc = docRepo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document", documentId));
        ActingPrincipal principal = SecurityUtils.requireUser();
        var verdict = authz.require(DOCUMENT_DOWNLOAD, doc.getOrg().getId());
        accessService.assertCanRead(principal, doc);
        audit.record("document.download", "document", documentId, doc.getOrg().getId(), null,
                Map.of("name", doc.getName()), verdict.authorizationBasis(), false);
        try {
            return new Download(doc.getId(), doc.getName(), doc.getContentType(), doc.getSizeBytes(),
                    fileStorage.open(doc.getStoredName()));
        } catch (IOException e) {
            throw new NotFoundException("file", doc.getStoredName());
        }
    }

    /** Content for REST download (API-key or session caller). */
    @Transactional
    public Download downloadStreamFor(long documentId, ActingPrincipal principal) {
        DocumentMeta doc = docRepo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document", documentId));
        var verdict = authz.require(DOCUMENT_DOWNLOAD, doc.getOrg().getId());
        accessService.assertCanRead(principal, doc);
        audit.record("document.download", "document", documentId, doc.getOrg().getId(), null,
                Map.of("name", doc.getName()), verdict.authorizationBasis(), false);
        try {
            return new Download(doc.getId(), doc.getName(), doc.getContentType(), doc.getSizeBytes(),
                    fileStorage.open(doc.getStoredName()));
        } catch (IOException e) {
            throw new NotFoundException("file", doc.getStoredName());
        }
    }

    /**
     * Mints a signed link that lets an external recipient reopen one document.
     * Staff share the link from the account's Documents tab.
     */
    @Transactional(readOnly = true)
    public String mintShareGrant(long documentId, String recipient) {
        DocumentMeta doc = docRepo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document", documentId));
        authz.require(DOCUMENT_DOWNLOAD, doc.getOrg().getId());
        String grant = JWT.create()
                .withSubject(recipient == null || recipient.isBlank() ? "external-recipient" : recipient)
                .withClaim("org", doc.getOrg().getId())
                .withClaim("doc", documentId)
                .withExpiresAt(Instant.now().plus(java.time.Duration.ofDays(14)))
                .sign(Algorithm.HMAC256(shareSigningKey));
        return baseUrl + "/api/share/documents/" + documentId + "?grant=" + grant;
    }

    /** Opens a document referenced by a shared link. */
    @Transactional(readOnly = true)
    public Download openShared(long documentId, long orgId) {
        DocumentMeta doc = docRepo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document", documentId));
        if (!doc.getOrg().getId().equals(orgId)) {
            throw new NotFoundException("document", documentId);
        }
        try {
            return new Download(doc.getId(), doc.getName(), doc.getContentType(), doc.getSizeBytes(),
                    fileStorage.open(doc.getStoredName()));
        } catch (IOException e) {
            throw new NotFoundException("file", doc.getStoredName());
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("file name is required");
        }
        String cleaned = name.trim()
                .replaceAll("[\\r\\n\\t]", " ")
                .replaceAll("[\\\\/]", "_");
        if (cleaned.length() > 200) {
            cleaned = cleaned.substring(0, 200);
        }
        if (cleaned.isBlank()) {
            throw new ValidationException("file name is required");
        }
        return cleaned;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot);
    }
}
