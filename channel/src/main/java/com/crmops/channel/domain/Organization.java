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
import java.time.LocalDate;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // EAGER: plan is a tiny seeded reference table that is always displayed
    // alongside an organization; eager loading avoids lazy-init failures in
    // view renderers that run outside the service transaction.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrgStatus status;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(name = "billing_contact_id")
    private Long billingContactId;

    @Column(name = "health_score", nullable = false)
    private int healthScore;

    @Column(name = "health_rationale")
    private String healthRationale;

    private String industry;

    private String country;

    @Column(name = "employee_count")
    private Integer employeeCount;

    @Column(nullable = false)
    private int seats;

    private String website;

    @Column(name = "internal_notes")
    private String internalNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    public Organization(String name, Plan plan, OrgStatus status, Long ownerUserId, LocalDate renewalDate,
                        int healthScore, String healthRationale, String industry, String country,
                        Integer employeeCount, int seats, String website, String internalNotes, Instant now) {
        this.name = name;
        this.plan = plan;
        this.status = status;
        this.ownerUserId = ownerUserId;
        this.renewalDate = renewalDate;
        this.healthScore = healthScore;
        this.healthRationale = healthRationale;
        this.industry = industry;
        this.country = country;
        this.employeeCount = employeeCount;
        this.seats = seats;
        this.website = website;
        this.internalNotes = internalNotes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Plan getPlan() {
        return plan;
    }

    public OrgStatus getStatus() {
        return status;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public Long getBillingContactId() {
        return billingContactId;
    }

    public int getHealthScore() {
        return healthScore;
    }

    public String getHealthRationale() {
        return healthRationale;
    }

    public String getIndustry() {
        return industry;
    }

    public String getCountry() {
        return country;
    }

    public Integer getEmployeeCount() {
        return employeeCount;
    }

    public int getSeats() {
        return seats;
    }

    public String getWebsite() {
        return website;
    }

    public String getInternalNotes() {
        return internalNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateProfile(String name, String industry, String country, Integer employeeCount, int seats,
                              String website, Instant now) {
        this.name = name;
        this.industry = industry;
        this.country = country;
        this.employeeCount = employeeCount;
        this.seats = seats;
        this.website = website;
        this.updatedAt = now;
    }

    public void changePlan(Plan newPlan, Instant now) {
        this.plan = newPlan;
        this.updatedAt = now;
    }

    public void changeStatus(OrgStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    public void setRenewal(LocalDate renewalDate, Instant now) {
        this.renewalDate = renewalDate;
        this.updatedAt = now;
    }

    public void setBillingContact(Long contactId, Instant now) {
        this.billingContactId = contactId;
        this.updatedAt = now;
    }

    public void setOwner(Long ownerUserId, Instant now) {
        this.ownerUserId = ownerUserId;
        this.updatedAt = now;
    }

    public void updateHealth(int score, String rationale, Instant now) {
        this.healthScore = score;
        this.healthRationale = rationale;
        this.updatedAt = now;
    }

    public void setInternalNotes(String notes, Instant now) {
        this.internalNotes = notes;
        this.updatedAt = now;
    }

    /** Derived health bucket; score bands are a stable domain rule. */
    public String healthBucket() {
        if (healthScore >= 80) {
            return "EXCELLENT";
        }
        if (healthScore >= 65) {
            return "GOOD";
        }
        if (healthScore >= 45) {
            return "AT_RISK";
        }
        return "CRITICAL";
    }
}
