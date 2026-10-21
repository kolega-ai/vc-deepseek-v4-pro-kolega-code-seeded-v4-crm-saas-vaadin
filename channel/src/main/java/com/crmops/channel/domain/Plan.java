package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Seeded reference table for plan pricing. Fees are read server-side only and
 * displayed to users; they are never accepted from the client.
 */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_monthly_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseMonthlyPrice;

    @Column(name = "per_seat_monthly_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal perSeatMonthlyPrice;

    @Column(name = "seats_included", nullable = false)
    private int seatsIncluded;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String features;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    protected Plan() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getBaseMonthlyPrice() {
        return baseMonthlyPrice;
    }

    public BigDecimal getPerSeatMonthlyPrice() {
        return perSeatMonthlyPrice;
    }

    public int getSeatsIncluded() {
        return seatsIncluded;
    }

    public String getDescription() {
        return description;
    }

    public String getFeatures() {
        return features;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    /** MRR for an organization on this plan — computed server-side from the reference table. */
    public BigDecimal monthlyRevenueFor(int seats) {
        int extraSeats = Math.max(0, seats - seatsIncluded);
        return baseMonthlyPrice.add(perSeatMonthlyPrice.multiply(BigDecimal.valueOf(extraSeats)));
    }
}
