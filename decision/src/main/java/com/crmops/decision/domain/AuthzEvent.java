package com.crmops.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only authorization decision log. No endpoint can modify or delete rows. */
@Entity
@Table(name = "authz_events")
public class AuthzEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id")
    private Long userId;

    private String username;

    private String role;

    /** USER or API_KEY */
    private String principal;

    @Column(nullable = false)
    private String action;

    @Column(name = "org_id")
    private Long orgId;

    @Column(nullable = false)
    private boolean allowed;

    @Column(nullable = false)
    private String basis;

    protected AuthzEvent() {
    }

    public AuthzEvent(Long userId, String username, String role, String principal, String action,
                      Long orgId, boolean allowed, String basis) {
        this.occurredAt = Instant.now();
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.principal = principal;
        this.action = action;
        this.orgId = orgId;
        this.allowed = allowed;
        this.basis = basis;
    }
}
