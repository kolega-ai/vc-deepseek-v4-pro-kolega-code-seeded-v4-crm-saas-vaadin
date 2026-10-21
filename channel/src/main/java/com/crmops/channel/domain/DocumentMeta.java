package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "documents")
public class DocumentMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(nullable = false)
    private String name;

    /** Random internal file name inside the canonical storage root; never user-controlled. */
    @Column(name = "stored_name", nullable = false)
    private String storedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocVisibility visibility;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private String description;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected DocumentMeta() {
    }

    public DocumentMeta(Organization org, String name, String storedName, DocCategory category,
                        DocVisibility visibility, String contentType, long sizeBytes, String description,
                        Long uploadedBy, Instant now) {
        this.org = org;
        this.name = name;
        this.storedName = storedName;
        this.category = category;
        this.visibility = visibility;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.description = description;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrg() {
        return org;
    }

    public String getName() {
        return name;
    }

    public String getStoredName() {
        return storedName;
    }

    public DocCategory getCategory() {
        return category;
    }

    public DocVisibility getVisibility() {
        return visibility;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getDescription() {
        return description;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void update(String name, DocCategory category, DocVisibility visibility, String description) {
        this.name = name;
        this.category = category;
        this.visibility = visibility;
        this.description = description;
    }
}
