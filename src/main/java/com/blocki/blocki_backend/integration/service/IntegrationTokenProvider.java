package com.blocki.blocki_backend.integration.service;

import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides a connected provider token to internal application services only.
 */
public interface IntegrationTokenProvider {

    Optional<String> findAccessToken(UUID userId, IntegrationProvider provider);
}
