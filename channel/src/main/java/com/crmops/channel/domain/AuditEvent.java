package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit event. Every state transition and every privileged action
 * writes exactly one row with the actor, before/after values, timestamp and
 * the authorization basis returned by the decision tier. No role can modify
 * or delete audit records through any screen, endpoint or export.
 */
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

    public AuditEvent(Instant occurredAt, Long actorId, String actorUsername, String actorRole, String action,
                      String targetType, String targetId, Long orgId, String beforeJson, String afterJson,
                      String authorizationBasis, boolean customerVisible, String ipAddress) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.orgId = orgId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.authorizationBasis = authorizationBasis;
        this.customerVisible = customerVisible;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public String getAuthorizationBasis() {
        return authorizationBasis;
    }

    public boolean isCustomerVisible() {
        return customerVisible;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
