package com.crmops.channel.web;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.crmops.channel.service.DocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public entry point for the signed document links staff share with external
 * recipients. The link carries a compact grant that names the document and its
 * organization so the recipient needs no CrmOps account.
 */
@RestController
@RequestMapping("/api/share")
public class ShareLinkController {

    private final DocumentService documentService;

    public ShareLinkController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<InputStreamResource> open(@PathVariable long id,
                                                    @RequestParam("grant") String grant) {
        DecodedJWT claims = com.auth0.jwt.JWT.decode(grant);
        Long orgId = claims.getClaim("org").asLong();
        DocumentService.Download download = documentService.openShared(id, orgId == null ? -1L : orgId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .body(new InputStreamResource(download.stream()));
    }
}
