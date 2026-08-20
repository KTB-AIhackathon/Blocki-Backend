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
        name = "document_generation_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_document_generation_jobs_user_key",
                columnNames = {"user_id", "idempotency_key"}))
public class DocumentGenerationJob {

    private static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentGenerationJobStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "missing_sources", length = 200)
    private String missingSources = "";

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "idempotency_expires_at", nullable = false)
    private Instant idempotencyExpiresAt;

    protected DocumentGenerationJob() {
    }

    private DocumentGenerationJob(
            UUID userId,
            DocumentType documentType,
            String idempotencyKey,
            Instant createdAt,
            Instant idempotencyExpiresAt) {
        this.userId = userId;
        this.documentType = documentType;
        this.idempotencyKey = idempotencyKey;
        this.status = DocumentGenerationJobStatus.QUEUED;
        this.progress = 0;
        this.attempt = 1;
        this.maxAttempts = MAX_ATTEMPTS;
        this.createdAt = createdAt;
        this.retryable = true;
        this.idempotencyExpiresAt = idempotencyExpiresAt;
    }

    public static DocumentGenerationJob queue(
            UUID userId,
            DocumentType documentType,
            String idempotencyKey,
            Instant createdAt,
            Instant idempotencyExpiresAt) {
        return new DocumentGenerationJob(userId, documentType, idempotencyKey, createdAt, idempotencyExpiresAt);
    }

    public UUID getId() {
        return id;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public DocumentGenerationJobStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getUserId() { return userId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public boolean isRetryable() { return retryable; }
    public String getErrorCode() { return errorCode; }
    public Instant getIdempotencyExpiresAt() { return idempotencyExpiresAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getMissingSources() { return missingSources; }
    public UUID getDocumentId() { return documentId; }
    public UUID getVersionId() { return versionId; }

    public void start(Instant startedAt) {
        this.status = DocumentGenerationJobStatus.RUNNING;
        this.startedAt = startedAt;
        this.nextRetryAt = null;
        this.progress = 10;
    }

    public void succeed(UUID documentId, UUID versionId, Instant completedAt, String missingSources) {
        succeed(documentId, versionId, completedAt, missingSources, false);
    }

    public void succeed(
            UUID documentId, UUID versionId, Instant completedAt, String missingSources, boolean partial) {
        boolean incomplete = partial || (missingSources != null && !missingSources.isBlank());
        this.status = incomplete
                ? DocumentGenerationJobStatus.PARTIALLY_SUCCEEDED
                : DocumentGenerationJobStatus.SUCCEEDED;
        this.progress = 100;
        this.completedAt = completedAt;
        this.retryable = false;
        this.nextRetryAt = null;
        this.errorCode = null;
        this.missingSources = missingSources == null ? "" : missingSources;
        this.documentId = documentId;
        this.versionId = versionId;
    }

    public void retry(Instant nextRetryAt, String errorCode) {
        this.attempt++;
        this.status = DocumentGenerationJobStatus.QUEUED;
        this.progress = 0;
        this.nextRetryAt = nextRetryAt;
        this.errorCode = errorCode;
    }

    public void fail(Instant completedAt, String errorCode, boolean retryable) {
        this.status = DocumentGenerationJobStatus.FAILED;
        this.completedAt = completedAt;
        this.retryable = retryable;
        this.nextRetryAt = null;
        this.errorCode = errorCode;
    }

    public void recoverAfterAbandonment(Instant now) {
        if (attempt >= maxAttempts) {
            fail(now, "AI_PIPELINE_FAILED", true);
            return;
        }
        retry(now.plus(attempt == 1 ? java.time.Duration.ofSeconds(30) : java.time.Duration.ofMinutes(2)),
                "AI_PIPELINE_FAILED");
    }
}
