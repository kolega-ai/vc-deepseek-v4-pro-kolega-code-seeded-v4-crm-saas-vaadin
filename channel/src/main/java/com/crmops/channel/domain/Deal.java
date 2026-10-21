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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "deals")
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "deal_type", nullable = false)
    private DealType dealType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStage stage;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int probability;

    @Column(name = "close_date")
    private LocalDate closeDate;

    private String notes;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Deal() {
    }

    public Deal(Organization org, String name, DealType dealType, DealStage stage, BigDecimal amount,
                String currency, int probability, LocalDate closeDate, String notes, Long ownerUserId, Instant now) {
        this.org = org;
        this.name = name;
        this.dealType = dealType;
        this.stage = stage;
        this.amount = amount;
        this.currency = currency;
        this.probability = probability;
        this.closeDate = closeDate;
        this.notes = notes;
        this.ownerUserId = ownerUserId;
        this.createdAt = now;
        this.updatedAt = now;
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

    public DealType getDealType() {
        return dealType;
    }

    public DealStage getStage() {
        return stage;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public int getProbability() {
        return probability;
    }

    public LocalDate getCloseDate() {
        return closeDate;
    }

    public String getNotes() {
        return notes;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name, BigDecimal amount, int probability, LocalDate closeDate, String notes, Instant now) {
        this.name = name;
        this.amount = amount;
        this.probability = probability;
        this.closeDate = closeDate;
        this.notes = notes;
        this.updatedAt = now;
    }

    public void moveTo(DealStage newStage, Instant now) {
        this.stage = newStage;
        this.updatedAt = now;
    }

    public void setOwner(Long ownerUserId, Instant now) {
        this.ownerUserId = ownerUserId;
        this.updatedAt = now;
    }

    /** Expected value = amount × probability, fixed-precision arithmetic. */
    public BigDecimal expectedValue() {
        return amount.multiply(BigDecimal.valueOf(probability)).movePointLeft(2).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
