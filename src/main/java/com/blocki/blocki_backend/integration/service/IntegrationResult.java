package com.blocki.blocki_backend.integration.service;

import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import java.time.Instant;

public record IntegrationResult(
        IntegrationProvider provider,
        IntegrationStatus status,
        String accountLabel,
        Instant connectedAt,
        String errorCode
) {
}
