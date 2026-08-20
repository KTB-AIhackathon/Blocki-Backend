package com.blocki.blocki_backend.document.scheduler;

import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.service.DocumentGenerationAutomationService;
import com.blocki.blocki_backend.document.service.DocumentGenerationException;
import com.blocki.blocki_backend.document.service.DocumentGenerationResult;
import com.blocki.blocki_backend.document.service.DocumentGenerationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DocumentGenerationAutomationScheduler {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationAutomationScheduler.class);

    private final DocumentGenerationAutomationService automationService;
    private final DocumentGenerationService documentGenerationService;
    private final Clock clock;
    private LocalDateTime lastEvaluatedMinute;

    @Autowired
    public DocumentGenerationAutomationScheduler(
            DocumentGenerationAutomationService automationService,
            DocumentGenerationService documentGenerationService) {
        this(automationService, documentGenerationService, Clock.systemUTC());
    }

    DocumentGenerationAutomationScheduler(
            DocumentGenerationAutomationService automationService,
            DocumentGenerationService documentGenerationService,
            Clock clock) {
        this.automationService = automationService;
        this.documentGenerationService = documentGenerationService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void runWeeklyDocumentGeneration() {
        LocalDateTime current = LocalDateTime.now(clock.withZone(SCHEDULE_ZONE))
                .withSecond(0)
                .withNano(0);
        for (LocalDateTime scheduledAt : minutesToEvaluate(current)) {
            dispatch(scheduledAt);
        }
        lastEvaluatedMinute = current;
    }

    List<LocalDateTime> minutesToEvaluate(LocalDateTime current) {
        if (lastEvaluatedMinute == null) {
            return List.of(current);
        }
        if (!lastEvaluatedMinute.isBefore(current)) {
            return List.of();
        }
        List<LocalDateTime> minutes = new ArrayList<>();
        for (LocalDateTime cursor = lastEvaluatedMinute.plusMinutes(1); !cursor.isAfter(current); cursor = cursor.plusMinutes(1)) {
            minutes.add(cursor);
        }
        return minutes;
    }

    private void dispatch(LocalDateTime scheduledAt) {
        var userIds = automationService.findEnabledUserIds(
                scheduledAt.getDayOfWeek(), scheduledAt.toLocalTime());
        if (!userIds.isEmpty()) {
            log.info(
                    "Scheduled document generation tick uuid=- ts={} users={} day={} time={}",
                    clock.instant(),
                    userIds.size(),
                    scheduledAt.getDayOfWeek(),
                    scheduledAt.toLocalTime());
        }
        for (UUID userId : userIds) {
            if (!automationService.isGithubConnected(userId)) {
                log.warn(
                        "Scheduled document generation disabled uuid=- ts={} userId={} reason=github_disconnected",
                        clock.instant(),
                        userId);
                automationService.disableForGithubDisconnect(userId);
                continue;
            }
            request(userId, DocumentType.RESUME, scheduledAt);
            request(userId, DocumentType.PORTFOLIO, scheduledAt);
        }
    }

    private void request(UUID userId, DocumentType documentType, LocalDateTime scheduledAt) {
        String idempotencyKey = "document-generation-automation:"
                + scheduledAt.toLocalDate()
                + ":"
                + scheduledAt.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
                + ":"
                + documentType.name();
        Instant ts = clock.instant();
        try {
            DocumentGenerationResult queued = documentGenerationService.request(userId, documentType, idempotencyKey);
            String status = queued == null ? "-" : queued.status();
            boolean reused = !"QUEUED".equals(status) && !"RUNNING".equals(status);
            log.info(
                    reused
                            ? "Scheduled document generation reused uuid={} ts={} userId={} type={} status={}"
                            : "Scheduled document generation queued uuid={} ts={} userId={} type={} status={}",
                    queued == null ? "-" : queued.id(),
                    ts,
                    userId,
                    documentType,
                    status);
        } catch (DocumentGenerationException exception) {
            if (DocumentGenerationException.JOB_ALREADY_RUNNING.equals(exception.getCode())) {
                log.info(
                        "Scheduled document generation skipped uuid=- ts={} userId={} type={}",
                        ts,
                        userId,
                        documentType);
                return;
            }
            log.error(
                    "Scheduled document generation could not be queued uuid=- ts={} userId={} type={}",
                    ts,
                    userId,
                    documentType,
                    exception);
        } catch (RuntimeException exception) {
            log.error(
                    "Scheduled document generation could not be queued uuid=- ts={} userId={} type={}",
                    ts,
                    userId,
                    documentType,
                    exception);
        }
    }
}
