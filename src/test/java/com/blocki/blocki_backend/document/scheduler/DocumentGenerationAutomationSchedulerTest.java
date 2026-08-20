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
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
    void uses_the_same_keys_when_the_scheduler_method_is_invoked_twice() {
        UUID userId = UUID.randomUUID();
        when(automationService.findEnabledUserIds(DayOfWeek.MONDAY, LocalTime.of(21, 0)))
                .thenReturn(List.of(userId));
        when(automationService.isGithubConnected(userId)).thenReturn(true);

        scheduler.runWeeklyDocumentGeneration();
        scheduler.runWeeklyDocumentGeneration();

        verify(documentGenerationService, times(2)).request(userId, DocumentType.RESUME, RESUME_KEY);
        verify(documentGenerationService, times(2)).request(userId, DocumentType.PORTFOLIO, PORTFOLIO_KEY);
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
