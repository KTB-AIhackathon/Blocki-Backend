package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import com.blocki.blocki_backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final List<DocumentGenerationJobStatus> ACTIVE_STATUSES = List.of(
            DocumentGenerationJobStatus.QUEUED,
            DocumentGenerationJobStatus.RUNNING);

    private final DocumentGenerationJobRepository jobRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public DocumentGenerationService(DocumentGenerationJobRepository jobRepository, UserRepository userRepository) {
        this(jobRepository, userRepository, Clock.systemUTC());
    }

    public DocumentGenerationService(DocumentGenerationJobRepository jobRepository, UserRepository userRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public DocumentGenerationResult request(UUID userId, DocumentType documentType, String idempotencyKey) {
        if (userId == null || documentType == null || idempotencyKey == null
                || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException("Invalid document generation request");
        }
        userRepository.findWithLockById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user does not exist"));
        Instant now = clock.instant();
        return jobRepository.findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(userId, idempotencyKey, now)
                .map(existing -> existing.getDocumentType() == documentType
                        ? DocumentGenerationResult.from(existing)
                        : throwIdempotencyKeyReused())
                .orElseGet(() -> queueAfterExpiredKeyCleanup(userId, documentType, idempotencyKey, now));
    }

    public DocumentGenerationResult get(UUID userId, UUID jobId) {
        DocumentGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(DocumentGenerationException::jobNotFound);
        if (!job.getUserId().equals(userId)) {
            throw DocumentGenerationException.forbidden();
        }
        return DocumentGenerationResult.from(job);
    }

    private DocumentGenerationResult queueNewJob(
            UUID userId, DocumentType documentType, String idempotencyKey, Instant now) {
        jobRepository.findByUserIdAndDocumentTypeAndStatusIn(userId, documentType, ACTIVE_STATUSES)
                .ifPresent(existing -> {
                    throw DocumentGenerationException.jobAlreadyRunning(existing.getId());
                });

        DocumentGenerationJob job = DocumentGenerationJob.queue(
                userId,
                documentType,
                idempotencyKey,
                now,
                now.plus(IDEMPOTENCY_TTL));
        return DocumentGenerationResult.from(jobRepository.save(job));
    }

    private DocumentGenerationResult queueAfterExpiredKeyCleanup(
            UUID userId, DocumentType documentType, String idempotencyKey, Instant now) {
        jobRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .filter(existing -> !existing.getIdempotencyExpiresAt().isAfter(now))
                .ifPresent(jobRepository::delete);
        return queueNewJob(userId, documentType, idempotencyKey, now);
    }

    private DocumentGenerationResult throwIdempotencyKeyReused() {
        throw DocumentGenerationException.idempotencyKeyReused();
    }
}
