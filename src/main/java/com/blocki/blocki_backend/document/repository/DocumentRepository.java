package com.blocki.blocki_backend.document.repository;

import com.blocki.blocki_backend.document.entity.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUserId(UUID userId);

    Optional<Document> findByUserIdAndType(UUID userId, com.blocki.blocki_backend.document.entity.DocumentType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Document> findWithLockByUserIdAndType(UUID userId, com.blocki.blocki_backend.document.entity.DocumentType type);
}
