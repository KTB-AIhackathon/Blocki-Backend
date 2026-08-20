package com.blocki.blocki_backend.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_publish_logs")
public class DocumentPublishLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "notion_status", nullable = false, length = 20)
    private String notionStatus;

    @Column(name = "notion_page_id", length = 80)
    private String notionPageId = "";

    @Column(name = "notion_detail", length = 500)
    private String notionDetail = "";

    @Column(name = "document_persisted", nullable = false)
    private boolean documentPersisted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentPublishLog() {
    }

    public static DocumentPublishLog create(
            UUID jobId,
            UUID userId,
            DocumentType documentType,
            int attempt,
            String notionStatus,
            String notionPageId,
            String notionDetail,
            boolean documentPersisted,
            Instant createdAt) {
        DocumentPublishLog row = new DocumentPublishLog();
        row.jobId = jobId;
        row.userId = userId;
        row.documentType = documentType;
        row.attempt = attempt;
        row.notionStatus = notionStatus == null || notionStatus.isBlank() ? "none" : notionStatus;
        row.notionPageId = notionPageId == null ? "" : notionPageId;
        row.notionDetail = truncate(notionDetail);
        row.documentPersisted = documentPersisted;
        row.createdAt = createdAt;
        return row;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getNotionStatus() {
        return notionStatus;
    }

    public String getNotionPageId() {
        return notionPageId;
    }

    public String getNotionDetail() {
        return notionDetail;
    }

    public boolean isDocumentPersisted() {
        return documentPersisted;
    }
}
