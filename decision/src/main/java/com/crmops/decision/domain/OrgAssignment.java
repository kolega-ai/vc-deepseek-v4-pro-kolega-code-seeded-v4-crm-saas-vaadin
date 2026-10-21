package com.crmops.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "org_assignments", uniqueConstraints = @UniqueConstraint(name = "uq_assignment", columnNames = {"user_id", "org_id"}))
public class OrgAssignment {

    public static final String RELATION_OWNER = "OWNER";
    public static final String RELATION_SUPPORT = "SUPPORT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String relation;

    protected OrgAssignment() {
    }

    public OrgAssignment(Long userId, Long orgId, String relation) {
        this.userId = userId;
        this.orgId = orgId;
        this.relation = relation;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getRelation() {
        return relation;
    }
}
