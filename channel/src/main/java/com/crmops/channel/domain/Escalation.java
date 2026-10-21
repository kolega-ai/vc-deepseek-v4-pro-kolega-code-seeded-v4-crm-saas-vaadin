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
@Table(name = "escalations")
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationStatus status;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached;

    protected Escalation() {
    }

    public Escalation(Organization org, String subject, String description, Severity severity,
                      EscalationStatus status, Long assigneeId, Long createdBy, Instant now) {
        this.org = org;
        this.subject = subject;
        this.description = description;
        this.severity = severity;
        this.status = status;
        this.assigneeId = assigneeId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
        this.slaDueAt = com.crmops.common.core.SlaPolicy.slaDueAt(severity.name(), now);
    }

    public Long getId() {
        return id;
    }

    public Organization getOrg() {
        return org;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public Severity getSeverity() {
        return severity;
    }

    public EscalationStatus getStatus() {
        return status;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public boolean isSlaBreached() {
        return slaBreached;
    }

    public void moveTo(EscalationStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
        if (newStatus == EscalationStatus.RESOLVED) {
            this.resolvedAt = now;
        }
        if (newStatus == EscalationStatus.CLOSED) {
            this.closedAt = now;
        }
    }

    public void assign(Long assigneeId, Instant now) {
        this.assigneeId = assigneeId;
        this.updatedAt = now;
    }

    public void flagSlaBreached(Instant now) {
        this.slaBreached = true;
        this.updatedAt = now;
    }

    public void updateDescription(String subject, String description, Instant now) {
        this.subject = subject;
        this.description = description;
        this.updatedAt = now;
    }
}
