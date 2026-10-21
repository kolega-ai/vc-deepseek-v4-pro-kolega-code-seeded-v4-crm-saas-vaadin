package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Channel-side mirror of the decision tier's identity records. The decision
 * tier is authoritative for authentication and authorization; this table only
 * exists for referential integrity of domain data (assignees, owners, audit
 * actors). Any mutation is pushed to decision via the sync API.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "customer_org_id")
    private Long customerOrgId;

    private String title;

    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccount() {
    }

    public UserAccount(Long id, String username, String displayName, String email, Role role, boolean enabled,
                       Long customerOrgId, String title, String phone, Instant now) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.customerOrgId = customerOrgId;
        this.title = title;
        this.phone = phone;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Long getCustomerOrgId() {
        return customerOrgId;
    }

    public String getTitle() {
        return title;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void applyProfile(String displayName, String title, String phone, Instant now) {
        this.displayName = displayName;
        this.title = title;
        this.phone = phone;
        this.updatedAt = now;
    }

    public void changeEmail(String email, Instant now) {
        this.email = email;
        this.updatedAt = now;
    }

    public void changeRole(Role role, Instant now) {
        this.role = role;
        this.updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void setCustomerOrgId(Long customerOrgId, Instant now) {
        this.customerOrgId = customerOrgId;
        this.updatedAt = now;
    }
}
