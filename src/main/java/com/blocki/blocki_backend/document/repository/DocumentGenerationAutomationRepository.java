package com.blocki.blocki_backend.document.repository;

import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentGenerationAutomationRepository extends JpaRepository<DocumentGenerationAutomation, UUID> {

    Optional<DocumentGenerationAutomation> findByUserId(UUID userId);

    List<DocumentGenerationAutomation> findByEnabledTrue();
}
