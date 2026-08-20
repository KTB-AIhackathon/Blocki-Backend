package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentGenerationJobTest {

    @Test
    void records_partial_success_and_retry_state() {
        Instant createdAt = Instant.parse("2026-08-20T00:00:00Z");
        DocumentGenerationJob job = DocumentGenerationJob.queue(
                UUID.randomUUID(), DocumentType.PORTFOLIO, "key", createdAt, createdAt.plusSeconds(86_400));

        job.start(createdAt.plusSeconds(1));
        job.retry(createdAt.plusSeconds(31), "AI_PIPELINE_FAILED");
        job.succeed(UUID.randomUUID(), UUID.randomUUID(), createdAt.plusSeconds(35), "GITHUB");

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.PARTIALLY_SUCCEEDED);
        assertThat(job.getAttempt()).isEqualTo(2);
        assertThat(job.getMissingSources()).isEqualTo("GITHUB");
        assertThat(job.isRetryable()).isFalse();
    }

    @Test
    void records_partial_success_with_empty_missing_sources() {
        Instant createdAt = Instant.parse("2026-08-20T00:00:00Z");
        DocumentGenerationJob job = DocumentGenerationJob.queue(
                UUID.randomUUID(), DocumentType.PORTFOLIO, "key", createdAt, createdAt.plusSeconds(86_400));

        job.start(createdAt.plusSeconds(1));
        job.succeed(UUID.randomUUID(), UUID.randomUUID(), createdAt.plusSeconds(35), "", true);

        assertThat(job.getStatus()).isEqualTo(DocumentGenerationJobStatus.PARTIALLY_SUCCEEDED);
        assertThat(job.getMissingSources()).isEmpty();
    }
}
