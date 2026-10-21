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

@Entity
@Table(name = "escalation_comments")
public class EscalationComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escalation_id")
    private Escalation escalation;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_role")
    private String authorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentVisibility visibility;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EscalationComment() {
    }

    public EscalationComment(Escalation escalation, Long authorId, String authorRole,
                             CommentVisibility visibility, String body, Instant now) {
        this.escalation = escalation;
        this.authorId = authorId;
        this.authorRole = authorRole;
        this.visibility = visibility;
        this.body = body;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Escalation getEscalation() {
        return escalation;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorRole() {
        return authorRole;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
