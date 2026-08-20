package com.blocki.blocki_backend.document.repository;

import com.blocki.blocki_backend.document.entity.DocumentPublishLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPublishLogRepository extends JpaRepository<DocumentPublishLog, UUID> {

    List<DocumentPublishLog> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
