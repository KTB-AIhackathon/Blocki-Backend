package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record DocumentGenerationResult(
        UUID id,
        String type,
        String status,
        int progress,
        int attempt,
        int maxAttempts,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        boolean retryable,
        Instant nextRetryAt,
        String errorCode,
        List<String> missingSources,
        UUID documentId,
        UUID versionId) {

    public static DocumentGenerationResult from(DocumentGenerationJob job) {
        return new DocumentGenerationResult(
                job.getId(),
                "DOCUMENT_GENERATION",
                job.getStatus().name(),
                job.getProgress(),
                job.getAttempt(),
                job.getMaxAttempts(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.isRetryable(),
                job.getNextRetryAt(),
                job.getErrorCode(),
                job.getMissingSources().isBlank()
                        ? List.of()
                        : Arrays.asList(job.getMissingSources().split(",")),
                job.getDocumentId(),
                job.getVersionId());
    }
}
