package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.common.exception.BusinessException;
import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationResponse;
import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;
import com.blocki.blocki_backend.document.repository.DocumentGenerationAutomationRepository;
import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import com.blocki.blocki_backend.integration.repository.IntegrationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationAutomationService {

    private final DocumentGenerationAutomationRepository automationRepository;
    private final IntegrationRepository integrationRepository;
    private final Clock clock;

    @Autowired
    public DocumentGenerationAutomationService(
            DocumentGenerationAutomationRepository automationRepository,
            IntegrationRepository integrationRepository) {
        this(automationRepository, integrationRepository, Clock.systemUTC());
    }

    DocumentGenerationAutomationService(
            DocumentGenerationAutomationRepository automationRepository,
            IntegrationRepository integrationRepository,
            Clock clock) {
        this.automationRepository = automationRepository;
        this.integrationRepository = integrationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DocumentGenerationAutomationResponse get(UUID userId) {
        boolean enabled = automationRepository.findByUserId(userId)
                .map(DocumentGenerationAutomation::isEnabled)
                .orElse(false);
        return DocumentGenerationAutomationResponse.of(enabled);
    }

    @Transactional
    public DocumentGenerationAutomationResponse update(UUID userId, boolean enabled) {
        if (enabled) {
            requireConnectedGithub(userId);
        }

        Instant now = clock.instant();
        DocumentGenerationAutomation automation = automationRepository.findByUserId(userId)
                .orElseGet(() -> DocumentGenerationAutomation.create(userId, enabled, now));
        automation.changeEnabled(enabled, now);
        automationRepository.save(automation);
        return DocumentGenerationAutomationResponse.of(automation.isEnabled());
    }

    @Transactional
    public void disableForGithubDisconnect(UUID userId) {
        automationRepository.findByUserId(userId).ifPresent(automation -> {
            automation.changeEnabled(false, clock.instant());
            automationRepository.save(automation);
        });
    }

    @Transactional(readOnly = true)
    public List<UUID> findEnabledUserIds() {
        return automationRepository.findByEnabledTrue().stream()
                .map(DocumentGenerationAutomation::getUserId)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isGithubConnected(UUID userId) {
        return integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.GITHUB)
                .map(Integration::getStatus)
                .filter(IntegrationStatus.CONNECTED::equals)
                .isPresent();
    }

    private void requireConnectedGithub(UUID userId) {
        integrationRepository.findWithLockByUserIdAndProvider(userId, IntegrationProvider.GITHUB)
                .filter(integration -> integration.getStatus() == IntegrationStatus.CONNECTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_INTEGRATION_REQUIRED));
    }
}
