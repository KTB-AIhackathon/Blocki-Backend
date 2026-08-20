package com.blocki.blocki_backend.document.service;

import com.blocki.blocki_backend.common.exception.BusinessException;
import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationResponse;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationUpdateRequest;
import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;
import com.blocki.blocki_backend.document.repository.DocumentGenerationAutomationRepository;
import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import com.blocki.blocki_backend.integration.repository.IntegrationRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentGenerationAutomationService {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationAutomationService.class);

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
                .map(DocumentGenerationAutomationResponse::of)
                .orElseGet(() -> DocumentGenerationAutomationResponse.of(false));
    }

    @Transactional
    public DocumentGenerationAutomationResponse update(UUID userId, boolean enabled) {
        return update(userId, new DocumentGenerationAutomationUpdateRequest(enabled, null));
    }

    @Transactional
    public DocumentGenerationAutomationResponse update(
            UUID userId, DocumentGenerationAutomationUpdateRequest request) {
        boolean enabled = request.enabled();
        if (enabled) {
            requireConnectedGithub(userId);
        }

        Instant now = clock.instant();
        ScheduleValues schedule = resolveSchedule(userId, request.schedule());
        DocumentGenerationAutomation automation = automationRepository.findByUserId(userId)
                .orElseGet(() -> DocumentGenerationAutomation.create(
                        userId, enabled, schedule.dayOfWeek(), schedule.time(), now));
        automation.changeSchedule(schedule.dayOfWeek(), schedule.time(), now);
        automation.changeEnabled(enabled, now);
        automationRepository.save(automation);
        log.info(
                "automation schedule saved uuid=- ts={} userId={} enabled={} day={} time={}",
                now,
                userId,
                enabled,
                schedule.dayOfWeek(),
                schedule.time().format(CLOCK));
        return DocumentGenerationAutomationResponse.of(automation);
    }

    @Transactional
    public void disableForGithubDisconnect(UUID userId) {
        automationRepository.findByUserId(userId).ifPresent(automation -> {
            automation.changeEnabled(false, clock.instant());
            automationRepository.save(automation);
        });
    }

    @Transactional(readOnly = true)
    public List<UUID> findEnabledUserIds(DayOfWeek dayOfWeek, LocalTime time) {
        LocalTime minute = minuteOf(time);
        return automationRepository.findByEnabledTrue().stream()
                .filter(automation -> automation.getScheduleDayOfWeek() == dayOfWeek)
                .filter(automation -> minuteOf(automation.getScheduleTime()).equals(minute))
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

    private ScheduleValues resolveSchedule(
            UUID userId, DocumentGenerationAutomationUpdateRequest.Schedule requestedSchedule) {
        if (requestedSchedule == null) {
            return automationRepository.findByUserId(userId)
                    .map(automation -> new ScheduleValues(
                            automation.getScheduleDayOfWeek(), automation.getScheduleTime()))
                    .orElseGet(() -> new ScheduleValues(DayOfWeek.MONDAY, LocalTime.of(21, 0)));
        }
        try {
            return new ScheduleValues(
                    DayOfWeek.valueOf(requestedSchedule.dayOfWeek()),
                    minuteOf(LocalTime.parse(requestedSchedule.time())));
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private static LocalTime minuteOf(LocalTime time) {
        return time == null ? LocalTime.of(21, 0) : time.truncatedTo(ChronoUnit.MINUTES);
    }

    private record ScheduleValues(DayOfWeek dayOfWeek, LocalTime time) {
    }
}
