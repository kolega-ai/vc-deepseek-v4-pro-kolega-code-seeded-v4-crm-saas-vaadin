package com.crmops.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(nullable = false)
    private String status;

    protected Organization() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public String getStatus() {
        return status;
    }
}
