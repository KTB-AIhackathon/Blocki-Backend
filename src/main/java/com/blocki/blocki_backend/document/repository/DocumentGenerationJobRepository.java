package com.blocki.blocki_backend.document.repository;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentGenerationJobRepository extends JpaRepository<DocumentGenerationJob, UUID> {

    Optional<DocumentGenerationJob> findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(
            UUID userId, String idempotencyKey, Instant now);

    Optional<DocumentGenerationJob> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<DocumentGenerationJob> findByUserIdAndDocumentTypeAndStatusIn(
            UUID userId, DocumentType documentType, Collection<DocumentGenerationJobStatus> statuses);

    Optional<DocumentGenerationJob> findByIdAndUserId(UUID id, UUID userId);

    Optional<DocumentGenerationJob> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from DocumentGenerationJob job where job.status = :status and "
            + "(job.nextRetryAt is null or job.nextRetryAt <= :now) order by job.createdAt asc")
    java.util.List<DocumentGenerationJob> findDueQueuedJobs(
            @Param("status") DocumentGenerationJobStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.List<DocumentGenerationJob> findByStatusAndStartedAtBefore(
            DocumentGenerationJobStatus status, Instant staleBefore);
}
