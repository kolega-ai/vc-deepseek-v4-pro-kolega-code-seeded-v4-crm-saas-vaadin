package com.crmops.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only audit writer used by batch jobs (SYSTEM actor). */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "before_json", columnDefinition = "text")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "text")
    private String afterJson;

    @Column(name = "authorization_basis", nullable = false)
    private String authorizationBasis;

    @Column(name = "customer_visible", nullable = false)
    private boolean customerVisible;

    @Column(name = "ip_address")
    private String ipAddress;

    protected AuditEvent() {
    }

    public static AuditEvent system(String action, String targetType, Long targetId, Long orgId,
                                    String basis, boolean customerVisible) {
        AuditEvent event = new AuditEvent();
        event.occurredAt = Instant.now();
        event.actorId = 0L;
        event.actorUsername = "system";
        event.actorRole = "SYSTEM";
        event.action = action;
        event.targetType = targetType;
        event.targetId = targetId == null ? null : String.valueOf(targetId);
        event.orgId = orgId;
        event.authorizationBasis = basis;
        event.customerVisible = customerVisible;
        return event;
    }

    public String getAction() {
        return action;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getAuthorizationBasis() {
        return authorizationBasis;
    }
}
