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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnBean(DocumentGenerationClient.class)
public class DocumentGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationWorker.class);

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
        Instant started = clock.instant();
        try {
            DocumentGenerationClient.Result result = client.generate(job);
            int markdownChars = result.markdown() == null ? 0 : result.markdown().length();
            if (!result.ok() || result.markdown() == null || result.markdown().isBlank()) {
                log.warn(
                        "document job failed jobId={} type={} httpBodyOk={} status={} errorCode={} markdownChars={} attempt={}/{} ms={}",
                        job.getId(),
                        job.getDocumentType(),
                        result.ok(),
                        result.status(),
                        result.errorCode(),
                        markdownChars,
                        job.getAttempt(),
                        job.getMaxAttempts(),
                        elapsedMs(started));
                fail(job, result.errorCode() == null ? "AI_PIPELINE_FAILED" : result.errorCode(), false);
                return;
            }
            String missingSources = String.join(",", result.missingSources());
            log.info(
                    "document job ok jobId={} type={} status={} markdownChars={} missing={} attempt={}/{} ms={}",
                    job.getId(),
                    job.getDocumentType(),
                    result.status(),
                    markdownChars,
                    missingSources,
                    job.getAttempt(),
                    job.getMaxAttempts(),
                    elapsedMs(started));
            completionService.complete(job, new GeneratedDocument(title(job), result.markdown()), missingSources);
        } catch (InternalAiClientException exception) {
            log.warn(
                    "document job transport jobId={} type={} category={} retryable={} attempt={}/{} ms={} cause={}",
                    job.getId(),
                    job.getDocumentType(),
                    exception.getCategory(),
                    exception.isRetryable(),
                    job.getAttempt(),
                    job.getMaxAttempts(),
                    elapsedMs(started),
                    causeName(exception));
            if (exception.isRetryable()) {
                retryOrFail(job, "AI_PIPELINE_FAILED");
            } else {
                fail(job, "AI_PIPELINE_FAILED", false);
            }
        } catch (RestClientException exception) {
            log.warn(
                    "document job rest jobId={} type={} attempt={}/{} ms={}",
                    job.getId(),
                    job.getDocumentType(),
                    job.getAttempt(),
                    job.getMaxAttempts(),
                    elapsedMs(started),
                    exception);
            retryOrFail(job, "AI_PIPELINE_FAILED");
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "document job invalid jobId={} type={} attempt={}/{} ms={}",
                    job.getId(),
                    job.getDocumentType(),
                    job.getAttempt(),
                    job.getMaxAttempts(),
                    elapsedMs(started),
                    exception);
            fail(job, "AI_PIPELINE_FAILED", false);
        }
    }

    private void retryOrFail(DocumentGenerationJob job, String errorCode) {
        if (job.getAttempt() >= job.getMaxAttempts()) {
            log.warn(
                    "document job exhausted jobId={} type={} attempt={}/{} errorCode={}",
                    job.getId(),
                    job.getDocumentType(),
                    job.getAttempt(),
                    job.getMaxAttempts(),
                    errorCode);
            fail(job, errorCode, true);
            return;
        }
        Duration delay = job.getAttempt() == 1 ? Duration.ofSeconds(30) : Duration.ofMinutes(2);
        log.warn(
                "document job retry jobId={} type={} attempt={}/{} delaySec={} errorCode={}",
                job.getId(),
                job.getDocumentType(),
                job.getAttempt(),
                job.getMaxAttempts(),
                delay.toSeconds(),
                errorCode);
        job.retry(clock.instant().plus(delay), errorCode);
        jobRepository.save(job);
    }

    private long elapsedMs(Instant started) {
        return Duration.between(started, clock.instant()).toMillis();
    }

    private static String causeName(InternalAiClientException exception) {
        Throwable cause = exception.getCause();
        return cause == null ? "-" : cause.getClass().getSimpleName();
    }

    private void fail(DocumentGenerationJob job, String errorCode, boolean retryable) {
        job.fail(clock.instant(), errorCode, retryable);
        jobRepository.save(job);
    }

    private static String title(DocumentGenerationJob job) {
        return job.getDocumentType().name().equals("RESUME") ? "이력서" : "포트폴리오";
    }
}
