package com.blocki.blocki_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_documents_user_type",
                columnNames = {"user_id", "type"}))
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Document() {
    }

    private Document(UUID userId, DocumentType type, String title, Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.createdAt = createdAt;
    }

    public static Document create(UUID userId, DocumentType type, String title, Instant createdAt) {
        return new Document(userId, type, title, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public DocumentType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
