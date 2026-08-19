package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.document.entity.Document;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.entity.DocumentVersion;
import com.blocki.blocki_backend.document.repository.DocumentRepository;
import com.blocki.blocki_backend.document.repository.DocumentVersionRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class DocumentQueryService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentQueryService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    public DocumentListResult list(UUID userId, DocumentType type, int page, int size, boolean ascending) {
        Comparator<DocumentSummary> comparator = Comparator.comparing(
                DocumentSummary::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        if (!ascending) {
            comparator = comparator.reversed();
        }
        List<Document> documents = documentRepository.findByUserId(userId).stream()
                .filter(document -> type == null || document.getType() == type)
                .toList();
        Map<UUID, List<DocumentVersion>> versionsByDocumentId = documents.isEmpty()
                ? Map.of()
                : documentVersionRepository.findByDocumentIdIn(
                                documents.stream().map(Document::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(DocumentVersion::getDocumentId));
        List<DocumentSummary> summaries = documents.stream()
                .map(document -> toSummary(document, versionsByDocumentId.getOrDefault(document.getId(), List.of())))
                .sorted(comparator)
                .toList();

        int from = Math.min(page * size, summaries.size());
        int to = Math.min(from + size, summaries.size());
        return new DocumentListResult(summaries.subList(from, to), page, size, summaries.size());
    }

    public DocumentContentResult latest(UUID userId, UUID documentId) {
        Document document = findOwned(userId, documentId);
        DocumentVersion version = documentVersionRepository.findFirstByDocumentIdOrderByVersionDesc(documentId)
                .orElseThrow(DocumentQueryException::versionNotFound);
        return toContent(document, version);
    }

    public DocumentVersionListResult versions(UUID userId, UUID documentId, int page, int size, boolean ascending) {
        findOwned(userId, documentId);
        Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
        var versions = documentVersionRepository.findByDocumentId(
                documentId, PageRequest.of(page, size, Sort.by(direction, "version")));
        return new DocumentVersionListResult(
                versions.getContent().stream().map(DocumentVersionSummary::from).toList(),
                page,
                size,
                versions.getTotalElements());
    }

    public DocumentContentResult version(UUID userId, UUID documentId, UUID versionId) {
        Document document = findOwned(userId, documentId);
        DocumentVersion version = documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(DocumentQueryException::versionNotFound);
        return toContent(document, version);
    }

    private DocumentSummary toSummary(Document document, List<DocumentVersion> versions) {
        DocumentVersion latest = versions.stream()
                .max(Comparator.comparing(DocumentVersion::getVersion))
                .orElse(null);
        Instant updatedAt = latest == null ? document.getCreatedAt() : latest.getCreatedAt();
        return new DocumentSummary(
                document.getId(),
                document.getType(),
                document.getTitle(),
                latest == null ? null : DocumentVersionSummary.from(latest),
                versions.size(),
                document.getCreatedAt(),
                updatedAt);
    }

    private Document findOwned(UUID userId, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(DocumentQueryException::documentNotFound);
        if (!document.getUserId().equals(userId)) {
            throw DocumentQueryException.forbidden();
        }
        return document;
    }

    private DocumentContentResult toContent(Document document, DocumentVersion version) {
        return new DocumentContentResult(
                document.getId(),
                document.getType(),
                document.getTitle(),
                version.getVersion(),
                version.getMarkdown(),
                version.getCreatedAt(),
                version.getSource());
    }

    public record DocumentListResult(List<DocumentSummary> items, int page, int size, long totalElements) {
    }

    public record DocumentSummary(
            UUID id,
            DocumentType type,
            String title,
            DocumentVersionSummary latestVersion,
            long versionCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record DocumentVersionListResult(List<DocumentVersionSummary> items, int page, int size, long totalElements) {
    }

    public record DocumentVersionSummary(UUID id, int version, Instant createdAt, String source) {
        private static DocumentVersionSummary from(DocumentVersion version) {
            return new DocumentVersionSummary(
                    version.getId(), version.getVersion(), version.getCreatedAt(), version.getSource());
        }
    }

    public record DocumentContentResult(
            UUID id,
            DocumentType type,
            String title,
            int version,
            String markdown,
            Instant createdAt,
            String source) {
    }
}
