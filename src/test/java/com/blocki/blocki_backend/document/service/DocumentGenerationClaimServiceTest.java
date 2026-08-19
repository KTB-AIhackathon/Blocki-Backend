package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DocumentGenerationClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void requeues_a_stale_running_job_with_the_first_retry_delay() {
        DocumentGenerationJobRepository repository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationJob job = job();
        job.start(NOW.minusSeconds(301));
        when(repository.findByStatusAndStartedAtBefore(DocumentGenerationJobStatus.RUNNING, NOW.minusSeconds(300)))
                .thenReturn(List.of(job));

        new DocumentGenerationClaimService(repository).recoverStaleRunningJobs(NOW);

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getAttempt()).isEqualTo(2);
        assertThat(job.getNextRetryAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void marks_a_stale_final_attempt_as_retryable_failure() {
        DocumentGenerationJobRepository repository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationJob job = job();
        job.retry(NOW, "AI_PIPELINE_FAILED");
        job.start(NOW.minusSeconds(302));
        job.retry(NOW, "AI_PIPELINE_FAILED");
        job.start(NOW.minusSeconds(301));
        when(repository.findByStatusAndStartedAtBefore(DocumentGenerationJobStatus.RUNNING, NOW.minusSeconds(300)))
                .thenReturn(List.of(job));

        new DocumentGenerationClaimService(repository).recoverStaleRunningJobs(NOW);

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.FAILED);
        assertThat(job.isRetryable()).isTrue();
    }

    @Test
    void claims_due_jobs_before_the_worker_calls_ai() {
        DocumentGenerationJobRepository repository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationJob job = job();
        when(repository.findDueQueuedJobs(eq(DocumentGenerationJobStatus.QUEUED), eq(NOW), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(job));

        List<DocumentGenerationJob> claimed = new DocumentGenerationClaimService(repository).claimDueQueuedJobs(NOW);

        assertThat(claimed).containsExactly(job);
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.RUNNING);
        assertThat(job.getStartedAt()).isEqualTo(NOW);
        verify(repository).findDueQueuedJobs(eq(DocumentGenerationJobStatus.QUEUED), eq(NOW), org.mockito.ArgumentMatchers.any());
    }

    private DocumentGenerationJob job() {
        return DocumentGenerationJob.queue(
                UUID.randomUUID(), DocumentType.RESUME, "key", NOW.minusSeconds(600), NOW.plusSeconds(86_400));
    }
}
