package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentPublishLog;
import com.blocki.blocki_backend.document.repository.DocumentPublishLogRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPublishLogService {

    private final DocumentPublishLogRepository repository;
    private final Clock clock = Clock.systemUTC();

    public DocumentPublishLogService(DocumentPublishLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            DocumentGenerationJob job, DocumentGenerationClient.Result result, boolean documentPersisted) {
        if (job == null || result == null || result.notion() == null) {
            return;
        }
        DocumentGenerationClient.NotionPublish notion = result.notion();
        repository.save(DocumentPublishLog.create(
                job.getId(),
                job.getUserId(),
                job.getDocumentType(),
                job.getAttempt(),
                notion.status(),
                notion.pageId(),
                notion.detail(),
                documentPersisted,
                clock.instant()));
    }
}
