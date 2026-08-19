package com.blocki.blocki_backend.document.repository;

import com.blocki.blocki_backend.document.entity.DocumentVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionDesc(UUID documentId);

    Page<DocumentVersion> findByDocumentId(UUID documentId, Pageable pageable);

    Optional<DocumentVersion> findByIdAndDocumentId(UUID id, UUID documentId);

    long countByDocumentId(UUID documentId);

    List<DocumentVersion> findByDocumentIdIn(Collection<UUID> documentIds);
}
