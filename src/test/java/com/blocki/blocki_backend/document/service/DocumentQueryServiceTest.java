package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.document.entity.Document;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.entity.DocumentVersion;
import com.blocki.blocki_backend.document.repository.DocumentRepository;
import com.blocki.blocki_backend.document.repository.DocumentVersionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    private DocumentQueryService service;

    @BeforeEach
    void setUp() {
        service = new DocumentQueryService(documentRepository, documentVersionRepository);
    }

    @Test
    void lists_only_the_requested_type_with_its_latest_version_metadata() {
        UUID userId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();
        Document resume = Document.create(
                userId, DocumentType.RESUME, "김블로 이력서", Instant.parse("2026-08-18T09:00:00Z"));
        ReflectionTestUtils.setField(resume, "id", resumeId);
        DocumentVersion latest = DocumentVersion.create(
                resumeId, 2, "# 김블로", Instant.parse("2026-08-19T09:00:00Z"));
        UUID latestVersionId = UUID.randomUUID();
        ReflectionTestUtils.setField(latest, "id", latestVersionId);

        when(documentRepository.findByUserId(userId)).thenReturn(List.of(resume));
        DocumentVersion first = DocumentVersion.create(
                resumeId, 1, "# 이전", Instant.parse("2026-08-18T09:00:00Z"));
        when(documentVersionRepository.findByDocumentIdIn(anyCollection())).thenReturn(List.of(first, latest));

        DocumentQueryService.DocumentListResult result = service.list(userId, DocumentType.RESUME, 0, 20, false);

        assertThat(result.items()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(resumeId);
            assertThat(summary.latestVersion().id()).isEqualTo(latestVersionId);
            assertThat(summary.versionCount()).isEqualTo(2);
            assertThat(summary.updatedAt()).isEqualTo(Instant.parse("2026-08-19T09:00:00Z"));
        });
        verify(documentVersionRepository).findByDocumentIdIn(java.util.List.of(resumeId));
        verify(documentVersionRepository, never()).findFirstByDocumentIdOrderByVersionDesc(resumeId);
        verify(documentVersionRepository, never()).countByDocumentId(resumeId);
    }
}
