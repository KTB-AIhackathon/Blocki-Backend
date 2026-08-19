package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.document.entity.Document;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentVersion;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import com.blocki.blocki_backend.document.repository.DocumentRepository;
import com.blocki.blocki_backend.document.repository.DocumentVersionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationCompletionService {
    private final DocumentGenerationJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final Clock clock = Clock.systemUTC();

    public DocumentGenerationCompletionService(DocumentGenerationJobRepository jobRepository, DocumentRepository documentRepository, DocumentVersionRepository versionRepository) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public void complete(DocumentGenerationJob job, GeneratedDocument generated, String missingSources) {
        validate(generated);
        Instant now = clock.instant();
        Document document = documentRepository.findWithLockByUserIdAndType(job.getUserId(), job.getDocumentType())
                .orElseGet(() -> documentRepository.save(Document.create(job.getUserId(), job.getDocumentType(), generated.title().trim(), now)));
        int nextVersion = versionRepository.findFirstByDocumentIdOrderByVersionDesc(document.getId())
                .map(version -> version.getVersion() + 1)
                .orElse(1);
        DocumentVersion version = versionRepository.save(DocumentVersion.create(document.getId(), nextVersion, generated.markdown().trim(), now));
        job.succeed(document.getId(), version.getId(), now, missingSources);
        jobRepository.save(job);
    }

    private void validate(GeneratedDocument generated) {
        if (generated == null || generated.title() == null || generated.title().isBlank() || generated.title().trim().length() > 200
                || generated.markdown() == null || generated.markdown().isBlank() || generated.markdown().trim().length() > 100_000) {
            throw new IllegalArgumentException("Invalid generated document");
        }
    }
}
