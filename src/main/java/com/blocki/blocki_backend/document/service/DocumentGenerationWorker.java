package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.ai.client.InternalAiClientException;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnBean(DocumentGenerationClient.class)
public class DocumentGenerationWorker {

    private final DocumentGenerationJobRepository jobRepository;
    private final DocumentGenerationClaimService claimService;
    private final DocumentGenerationCompletionService completionService;
    private final DocumentGenerationClient client;
    private final Clock clock;

    // The second constructor exists only so tests can hold time still. Without
    // this marker Spring sees two candidates and refuses to build the bean.
    @Autowired
    public DocumentGenerationWorker(
            DocumentGenerationJobRepository jobRepository,
            DocumentGenerationClaimService claimService,
            DocumentGenerationCompletionService completionService,
            DocumentGenerationClient client) {
        this(jobRepository, claimService, completionService, client, Clock.systemUTC());
    }

    DocumentGenerationWorker(
            DocumentGenerationJobRepository jobRepository,
            DocumentGenerationClaimService claimService,
            DocumentGenerationCompletionService completionService,
            DocumentGenerationClient client,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.claimService = claimService;
        this.completionService = completionService;
        this.client = client;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${document-generation.poll-delay-ms:1000}")
    public void processQueuedJobs() {
        Instant now = clock.instant();
        claimService.recoverStaleRunningJobs(now);
        claimService.claimDueQueuedJobs(now).forEach(this::process);
    }

    private void process(DocumentGenerationJob job) {
        Instant now = clock.instant();
        try {
            DocumentGenerationClient.Result result = client.generate(job);
            if (!result.ok() || result.markdown() == null || result.markdown().isBlank()) {
                fail(job, result.errorCode() == null ? "AI_PIPELINE_FAILED" : result.errorCode(), false);
                return;
            }
            String missingSources = String.join(",", result.missingSources());
            completionService.complete(job, new GeneratedDocument(title(job), result.markdown()), missingSources);
        } catch (InternalAiClientException exception) {
            if (exception.isRetryable()) {
                retryOrFail(job, "AI_PIPELINE_FAILED");
            } else {
                fail(job, "AI_PIPELINE_FAILED", false);
            }
        } catch (RestClientException exception) {
            retryOrFail(job, "AI_PIPELINE_FAILED");
        } catch (IllegalArgumentException exception) {
            fail(job, "AI_PIPELINE_FAILED", false);
        }
    }

    private void retryOrFail(DocumentGenerationJob job, String errorCode) {
        if (job.getAttempt() >= job.getMaxAttempts()) {
            fail(job, errorCode, true);
            return;
        }
        Duration delay = job.getAttempt() == 1 ? Duration.ofSeconds(30) : Duration.ofMinutes(2);
        job.retry(clock.instant().plus(delay), errorCode);
        jobRepository.save(job);
    }

    private void fail(DocumentGenerationJob job, String errorCode, boolean retryable) {
        job.fail(clock.instant(), errorCode, retryable);
        jobRepository.save(job);
    }

    private static String title(DocumentGenerationJob job) {
        return job.getDocumentType().name().equals("RESUME") ? "이력서" : "포트폴리오";
    }
}
