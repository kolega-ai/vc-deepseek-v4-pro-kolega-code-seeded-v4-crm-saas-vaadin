package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Which staff members may work on which organizations (mirrored to decision). */
@Entity
@Table(name = "org_assignments", uniqueConstraints = @UniqueConstraint(name = "uq_channel_assignment", columnNames = {"user_id", "org_id"}))
public class OrgAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentRelation relation;

    protected OrgAssignment() {
    }

    public OrgAssignment(Long userId, Long orgId, AssignmentRelation relation) {
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

    public AssignmentRelation getRelation() {
        return relation;
    }

    public void setRelation(AssignmentRelation relation) {
        this.relation = relation;
    }
}
