package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentPublishLog;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import com.blocki.blocki_backend.document.repository.DocumentPublishLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(DocumentPublishLogService.class)
class DocumentPublishLogServiceTest {

    @Autowired
    private DocumentPublishLogService publishLogService;

    @Autowired
    private DocumentPublishLogRepository repository;

    @Autowired
    private DocumentGenerationJobRepository jobRepository;

    @Test
    void records_a_notion_skip_even_when_the_document_row_was_not_saved() {
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        DocumentGenerationJob job = jobRepository.save(DocumentGenerationJob.queue(
                UUID.randomUUID(), DocumentType.PORTFOLIO, "publish-log", now, now.plusSeconds(86_400)));
        job.start(now);
        job = jobRepository.save(job);
        DocumentGenerationClient.Result result = new DocumentGenerationClient.Result(
                true,
                "partial",
                "# 본문",
                List.of(),
                null,
                false,
                new DocumentGenerationClient.NotionPublish(
                        "skipped", "", "parent unreadable (404)"));

        publishLogService.record(job, result, false);

        List<DocumentPublishLog> rows = repository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getNotionStatus()).isEqualTo("skipped");
        assertThat(rows.get(0).getNotionDetail()).isEqualTo("parent unreadable (404)");
        assertThat(rows.get(0).isDocumentPersisted()).isFalse();
    }
}
