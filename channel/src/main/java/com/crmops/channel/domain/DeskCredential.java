package com.crmops.channel.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Verifier for a support-desk operator who signs on from an on-premise kiosk. */
@Entity
@Table(name = "desk_credentials")
public class DeskCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String operator;

    @Column(nullable = false)
    private String verifier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeskCredential() {
    }

    public DeskCredential(String operator, String verifier, Instant now) {
        this.operator = operator;
        this.verifier = verifier;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOperator() {
        return operator;
    }

    public String getVerifier() {
        return verifier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setVerifier(String verifier) {
        this.verifier = verifier;
    }
}
