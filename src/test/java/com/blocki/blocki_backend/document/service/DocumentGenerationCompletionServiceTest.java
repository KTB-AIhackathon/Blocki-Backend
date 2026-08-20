package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import com.blocki.blocki_backend.document.repository.DocumentRepository;
import com.blocki.blocki_backend.document.repository.DocumentVersionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(DocumentGenerationCompletionService.class)
class DocumentGenerationCompletionServiceTest {

    @Autowired
    private DocumentGenerationCompletionService completionService;

    @Autowired
    private DocumentGenerationJobRepository jobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void four_arg_complete_commits_job_document_and_version_without_an_outer_transaction() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        UUID userId = UUID.randomUUID();
        DocumentGenerationJob job = jobRepository.save(DocumentGenerationJob.queue(
                userId, DocumentType.RESUME, "complete-key", now, now.plusSeconds(86_400)));
        job.start(now);
        job = jobRepository.save(job);

        completionService.complete(job, new GeneratedDocument("이력서", "# 본문"), "", true);

        DocumentGenerationJob stored = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(DocumentGenerationJobStatus.PARTIALLY_SUCCEEDED);
        assertThat(stored.getDocumentId()).isNotNull();
        assertThat(stored.getVersionId()).isNotNull();
        assertThat(documentRepository.findById(stored.getDocumentId())).isPresent();
        assertThat(versionRepository.findById(stored.getVersionId()))
                .get()
                .extracting(version -> version.getMarkdown())
                .isEqualTo("# 본문");
    }
}
