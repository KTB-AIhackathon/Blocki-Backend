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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationAutomationService {

    private static final Set<String> VALID_DAYS_OF_WEEK = Set.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

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
        return automationRepository.findByUserId(userId)
                .map(automation -> DocumentGenerationAutomationResponse.of(
                        automation.isEnabled(),
                        resolveDayOfWeek(automation.getDayOfWeek()),
                        resolveTime(automation.getTime())))
                .orElseGet(() -> DocumentGenerationAutomationResponse.of(
                        false, DocumentGenerationAutomation.DEFAULT_DAY_OF_WEEK, DocumentGenerationAutomation.DEFAULT_TIME));
    }

    /**
     * enabled만 바꾸고 요일·시간은 현재 저장된 값(없으면 기본값)을 그대로 유지한다.
     */
    @Transactional
    public DocumentGenerationAutomationResponse update(UUID userId, boolean enabled) {
        DocumentGenerationAutomation existing = automationRepository.findByUserId(userId).orElse(null);
        String dayOfWeek = existing != null ? resolveDayOfWeek(existing.getDayOfWeek()) : DocumentGenerationAutomation.DEFAULT_DAY_OF_WEEK;
        String time = existing != null ? resolveTime(existing.getTime()) : DocumentGenerationAutomation.DEFAULT_TIME;
        return update(userId, enabled, dayOfWeek, time);
    }

    @Transactional
    public DocumentGenerationAutomationResponse update(UUID userId, boolean enabled, String dayOfWeek, String time) {
        if (enabled) {
            requireConnectedGithub(userId);
        }

        String normalizedDayOfWeek = normalizeDayOfWeek(dayOfWeek);
        String normalizedTime = normalizeTime(time);

        Instant now = clock.instant();
        DocumentGenerationAutomation automation = automationRepository.findByUserId(userId)
                .orElseGet(() -> DocumentGenerationAutomation.create(userId, enabled, normalizedDayOfWeek, normalizedTime, now));
        automation.changeEnabled(enabled, now);
        automation.changeSchedule(normalizedDayOfWeek, normalizedTime, now);
        automationRepository.save(automation);
        return DocumentGenerationAutomationResponse.of(automation.isEnabled(), automation.getDayOfWeek(), automation.getTime());
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

    private String resolveDayOfWeek(String dayOfWeek) {
        return dayOfWeek != null ? dayOfWeek : DocumentGenerationAutomation.DEFAULT_DAY_OF_WEEK;
    }

    private String resolveTime(String time) {
        return time != null ? time : DocumentGenerationAutomation.DEFAULT_TIME;
    }

    private String normalizeDayOfWeek(String dayOfWeek) {
        if (dayOfWeek == null || !VALID_DAYS_OF_WEEK.contains(dayOfWeek)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "dayOfWeek는 MONDAY~SUNDAY 중 하나여야 합니다.");
        }
        return dayOfWeek;
    }

    private String normalizeTime(String time) {
        if (time == null || !TIME_PATTERN.matcher(time).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "time은 HH:mm 형식이어야 합니다.");
        }
        return time;
    }
}
