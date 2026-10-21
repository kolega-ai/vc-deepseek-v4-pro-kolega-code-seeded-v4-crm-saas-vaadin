package com.crmops.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "escalations")
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false)
    private String status;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "sla_breached", nullable = false)
    private boolean slaBreached;

    protected Escalation() {
    }

    public static Escalation create(long orgId, String subject, String severity, String status,
                                    Long assigneeId, Instant createdAt, Instant slaDueAt) {
        Escalation e = new Escalation();
        e.orgId = orgId;
        e.description = "description";
        e.subject = subject;
        e.severity = severity;
        e.status = status;
        e.assigneeId = assigneeId;
        e.createdAt = createdAt;
        e.updatedAt = createdAt;
        e.slaDueAt = slaDueAt;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getSubject() {
        return subject;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public boolean isSlaBreached() {
        return slaBreached;
    }

    public void flagSlaBreached(Instant now) {
        this.slaBreached = true;
    }
}
