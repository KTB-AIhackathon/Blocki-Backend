package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationClaimService {

    private static final int CLAIM_BATCH_SIZE = 10;
    private static final Duration RUNNING_TIMEOUT = Duration.ofMinutes(5);

    private final DocumentGenerationJobRepository jobRepository;

    public DocumentGenerationClaimService(DocumentGenerationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public void recoverStaleRunningJobs(Instant now) {
        jobRepository.findByStatusAndStartedAtBefore(
                        DocumentGenerationJobStatus.RUNNING, now.minus(RUNNING_TIMEOUT))
                .forEach(job -> job.recoverAfterAbandonment(now));
    }

    @Transactional
    public List<DocumentGenerationJob> claimDueQueuedJobs(Instant now) {
        List<DocumentGenerationJob> jobs = jobRepository.findDueQueuedJobs(
                DocumentGenerationJobStatus.QUEUED, now, PageRequest.of(0, CLAIM_BATCH_SIZE));
        jobs.forEach(job -> job.start(now));
        return jobs;
    }
}
