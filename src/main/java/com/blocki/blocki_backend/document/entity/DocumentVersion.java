package com.blocki.blocki_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "document_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_versions_document_version",
                columnNames = {"document_id", "version"}))
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String markdown;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentVersion() {
    }

    private DocumentVersion(UUID documentId, int version, String markdown, Instant createdAt) {
        this.documentId = documentId;
        this.version = version;
        this.markdown = markdown;
        this.source = "AI_GENERATED";
        this.createdAt = createdAt;
    }

    public static DocumentVersion create(UUID documentId, int version, String markdown, Instant createdAt) {
        return new DocumentVersion(documentId, version, markdown, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getVersion() {
        return version;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
