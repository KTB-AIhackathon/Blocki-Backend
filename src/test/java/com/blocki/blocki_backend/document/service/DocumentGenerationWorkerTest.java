package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.ai.client.InternalAiClientException;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.client.ResourceAccessException;

class DocumentGenerationWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void completes_a_job_with_the_validated_ai_markdown() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenReturn(new DocumentGenerationClient.Result(
                true, "proposed", "# 내용", List.of("GITHUB"), null));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        ArgumentCaptor<GeneratedDocument> generated = ArgumentCaptor.forClass(GeneratedDocument.class);
        verify(completionService).complete(
                any(), generated.capture(), org.mockito.ArgumentMatchers.eq("GITHUB"), org.mockito.ArgumentMatchers.eq(false));
        assertThat(generated.getValue()).isEqualTo(new GeneratedDocument("이력서", "# 내용"));
        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.RUNNING);
    }

    @Test
    void reschedules_a_transient_ai_failure_after_thirty_seconds() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenThrow(new ResourceAccessException("offline"));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getAttempt()).isEqualTo(2);
        assertThat(job.getNextRetryAt()).isEqualTo(NOW.plusSeconds(30));
        verify(jobRepository).save(job);
    }

    @Test
    void stores_a_non_empty_no_change_result_as_a_new_document_version() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenReturn(new DocumentGenerationClient.Result(
                true, "no_change", "# 새 문서", List.of(), null));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        verify(completionService).complete(any(),
                org.mockito.ArgumentMatchers.eq(new GeneratedDocument("이력서", "# 새 문서")),
                org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void reschedules_http_200_retryable_ai_failure_after_thirty_seconds() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenReturn(new DocumentGenerationClient.Result(
                false, "failed", null, List.of(), "internal", true));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getAttempt()).isEqualTo(2);
        assertThat(job.getNextRetryAt()).isEqualTo(NOW.plusSeconds(30));
        verify(jobRepository).save(job);
        Mockito.verifyNoInteractions(completionService);
    }

    @Test
    void fails_http_200_non_retryable_ai_failure_immediately() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenReturn(new DocumentGenerationClient.Result(
                false, "failed", null, List.of(), "missing_pat", false));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("missing_pat");
        assertThat(job.isRetryable()).isFalse();
        verify(jobRepository).save(job);
        Mockito.verifyNoInteractions(completionService);
    }

    @Test
    void records_partial_success_with_empty_missing_sources() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenReturn(new DocumentGenerationClient.Result(
                true, "partial", "# 문서", List.of(), null));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        verify(completionService).complete(
                any(),
                org.mockito.ArgumentMatchers.eq(new GeneratedDocument("이력서", "# 문서")),
                org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void fails_without_retry_for_invalid_internal_ai_response() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenThrow(new InternalAiClientException(
                InternalAiClientException.Category.INVALID_RESPONSE));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.FAILED);
        assertThat(job.isRetryable()).isFalse();
        verify(jobRepository).save(job);
    }

    @Test
    void retries_after_internal_ai_server_failure() {
        DocumentGenerationJobRepository jobRepository = Mockito.mock(DocumentGenerationJobRepository.class);
        DocumentGenerationClaimService claimService = Mockito.mock(DocumentGenerationClaimService.class);
        DocumentGenerationCompletionService completionService = Mockito.mock(DocumentGenerationCompletionService.class);
        DocumentGenerationClient client = Mockito.mock(DocumentGenerationClient.class);
        DocumentGenerationJob job = job();
        job.start(NOW);
        when(claimService.claimDueQueuedJobs(NOW)).thenReturn(List.of(job));
        when(client.generate(any())).thenThrow(new InternalAiClientException(
                InternalAiClientException.Category.SERVER_ERROR));

        worker(jobRepository, claimService, completionService, client).processQueuedJobs();

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.QUEUED);
        assertThat(job.getAttempt()).isEqualTo(2);
        verify(jobRepository).save(job);
    }

    private DocumentGenerationWorker worker(
            DocumentGenerationJobRepository jobRepository,
            DocumentGenerationClaimService claimService,
            DocumentGenerationCompletionService completionService,
            DocumentGenerationClient client) {
        return new DocumentGenerationWorker(
                jobRepository,
                claimService,
                completionService,
                client,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DocumentGenerationJob job() {
        return DocumentGenerationJob.queue(
                UUID.randomUUID(), DocumentType.RESUME, "key", NOW, NOW.plusSeconds(86_400));
    }
}
