package com.crmops.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Projection of the channel tier's email_outbox table (batch is read/write here only for status). */
@Entity
@Table(name = "email_outbox")
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private String template;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected EmailOutbox() {
    }

    public static EmailOutbox create(String toAddress, String subject, String body, String template,
                                     String status, Instant createdAt) {
        EmailOutbox e = new EmailOutbox();
        e.toAddress = toAddress;
        e.subject = subject;
        e.body = body;
        e.template = template;
        e.status = status;
        e.createdAt = createdAt;
        return e;
    }

    public Long getId() {
        return id;
    }

    public String getToAddress() {
        return toAddress;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getTemplate() {
        return template;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markSent(Instant now) {
        this.status = "SENT";
        this.sentAt = now;
    }
}
