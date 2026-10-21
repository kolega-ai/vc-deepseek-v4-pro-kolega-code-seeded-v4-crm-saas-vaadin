package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id")
    private Organization org;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String title;

    /** Comma-separated permission tags, e.g. BILLING_CONTACT,TECHNICAL_CONTACT,PORTAL_ADMIN. */
    private String permissions;

    @Column(name = "is_billing", nullable = false)
    private boolean billing;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    /** Set when the contact has a portal login. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contact() {
    }

    public Contact(Organization org, String firstName, String lastName, String email, String phone, String title,
                   String permissions, boolean billing, boolean primary, Long userId, Instant now) {
        this.org = org;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.title = title;
        this.permissions = permissions;
        this.billing = billing;
        this.primary = primary;
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrg() {
        return org;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getTitle() {
        return title;
    }

    public String getPermissions() {
        return permissions;
    }

    public boolean isBilling() {
        return billing;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public void update(String firstName, String lastName, String phone, String title, String permissions,
                       boolean billing, boolean primary, Instant now) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.title = title;
        this.permissions = permissions;
        this.billing = billing;
        this.primary = primary;
        this.updatedAt = now;
    }

    public void linkUser(Long userId, Instant now) {
        this.userId = userId;
        this.updatedAt = now;
    }
}
