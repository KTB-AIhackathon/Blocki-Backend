package com.blocki.blocki_backend.integration.repository;

import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    Optional<Integration> findByUserIdAndProvider(UUID userId, IntegrationProvider provider);
}
