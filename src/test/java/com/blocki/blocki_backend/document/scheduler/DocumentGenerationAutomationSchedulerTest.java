package com.blocki.blocki_backend.document.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.service.DocumentGenerationAutomationService;
import com.blocki.blocki_backend.document.service.DocumentGenerationException;
import com.blocki.blocki_backend.document.service.DocumentGenerationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationAutomationSchedulerTest {

    private static final String RESUME_KEY = "document-generation-automation:2026-08-24:RESUME";
    private static final String PORTFOLIO_KEY = "document-generation-automation:2026-08-24:PORTFOLIO";

    @Mock
    private DocumentGenerationAutomationService automationService;

    @Mock
    private DocumentGenerationService documentGenerationService;

    private DocumentGenerationAutomationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DocumentGenerationAutomationScheduler(
                automationService,
                documentGenerationService,
                Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void declares_the_fixed_kst_monday_schedule() throws Exception {
        Scheduled scheduled = DocumentGenerationAutomationScheduler.class
                .getDeclaredMethod("runWeeklyDocumentGeneration")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 * * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void queues_both_document_types_with_kst_monday_keys() {
        UUID userId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(userId));
        when(automationService.isGithubConnected(userId)).thenReturn(true);

        scheduler.runWeeklyDocumentGeneration();

        verify(documentGenerationService).request(userId, DocumentType.RESUME, RESUME_KEY);
        verify(documentGenerationService).request(userId, DocumentType.PORTFOLIO, PORTFOLIO_KEY);
    }

    @Test
    void turns_off_stale_automation_without_queuing_a_job() {
        UUID userId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(userId));
        when(automationService.isGithubConnected(userId)).thenReturn(false);

        scheduler.runWeeklyDocumentGeneration();

        verify(automationService).disableForGithubDisconnect(userId);
        verifyNoInteractions(documentGenerationService);
    }

    @Test
    void skips_only_the_type_with_an_active_job() {
        UUID userId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(userId));
        when(automationService.isGithubConnected(userId)).thenReturn(true);
        when(documentGenerationService.request(userId, DocumentType.RESUME, RESUME_KEY))
                .thenThrow(DocumentGenerationException.jobAlreadyRunning(UUID.randomUUID()));

        scheduler.runWeeklyDocumentGeneration();

        verify(documentGenerationService).request(userId, DocumentType.PORTFOLIO, PORTFOLIO_KEY);
    }

    @Test
    void does_not_recheck_an_already_processed_minute() {
        UUID userId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(userId));
        when(automationService.isGithubConnected(userId)).thenReturn(true);

        scheduler.runWeeklyDocumentGeneration();
        scheduler.runWeeklyDocumentGeneration();

        verify(documentGenerationService, times(1)).request(userId, DocumentType.RESUME, RESUME_KEY);
        verify(documentGenerationService, times(1)).request(userId, DocumentType.PORTFOLIO, PORTFOLIO_KEY);
    }

    @Test
    void first_tick_checks_only_the_current_kst_minute() {
        LocalDateTime current = LocalDateTime.of(2026, 8, 24, 21, 0);
        assertThat(scheduler.minutesToEvaluate(current)).containsExactly(current);
        assertThat(scheduler.minutesToEvaluate(current.plusMinutes(2))).containsExactly(current.plusMinutes(2));
    }

    @Test
    void catches_up_each_unprocessed_minute_once() {
        AdjustableClock clock = new AdjustableClock(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);
        DocumentGenerationAutomationScheduler catching = new DocumentGenerationAutomationScheduler(
                automationService, documentGenerationService, clock);
        UUID userId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(userId));
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 1)))
                .thenReturn(List.of());
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 2)))
                .thenReturn(List.of());
        when(automationService.isGithubConnected(userId)).thenReturn(true);

        catching.runWeeklyDocumentGeneration();
        clock.set(Instant.parse("2026-08-24T12:02:00Z"));
        catching.runWeeklyDocumentGeneration();

        verify(automationService).findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0));
        verify(automationService).findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 1));
        verify(automationService).findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 2));
        verify(documentGenerationService, times(1)).request(userId, DocumentType.RESUME, RESUME_KEY);
    }

    @Test
    void the_deployed_scheduler_has_two_threads() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yaml"));
        assertThat(yaml).contains("      pool:\n        size: 2");
    }

    private static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private AdjustableClock(Instant start, ZoneId zone) {
            this(new AtomicReference<>(start), zone);
        }

        private AdjustableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void set(Instant next) {
            instant.set(next);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new AdjustableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    @Test
    void continues_with_the_next_user_after_an_unexpected_queue_failure() {
        UUID failedUserId = UUID.randomUUID();
        UUID nextUserId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(failedUserId, nextUserId));
        when(automationService.isGithubConnected(failedUserId)).thenReturn(true);
        when(automationService.isGithubConnected(nextUserId)).thenReturn(true);
        when(documentGenerationService.request(failedUserId, DocumentType.RESUME, RESUME_KEY))
                .thenThrow(new IllegalStateException("queue unavailable"));

        scheduler.runWeeklyDocumentGeneration();

        verify(documentGenerationService).request(nextUserId, DocumentType.RESUME, RESUME_KEY);
        verify(documentGenerationService).request(nextUserId, DocumentType.PORTFOLIO, PORTFOLIO_KEY);
    }

    @Test
    void queues_when_the_saved_minute_matches_the_current_kst_clock() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationAutomationScheduler minuteScheduler = new DocumentGenerationAutomationScheduler(
                automationService,
                documentGenerationService,
                Clock.fixed(Instant.parse("2026-08-24T12:20:00Z"), ZoneOffset.UTC));
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 20)))
                .thenReturn(List.of(userId));
        when(automationService.isGithubConnected(userId)).thenReturn(true);

        minuteScheduler.runWeeklyDocumentGeneration();

        verify(documentGenerationService).request(userId, DocumentType.RESUME, RESUME_KEY);
        verify(documentGenerationService).request(userId, DocumentType.PORTFOLIO, PORTFOLIO_KEY);
    }
}
