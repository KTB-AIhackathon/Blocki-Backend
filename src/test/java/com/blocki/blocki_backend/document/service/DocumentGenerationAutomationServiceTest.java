package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.common.exception.BusinessException;
import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationResponse;
import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;
import com.blocki.blocki_backend.document.repository.DocumentGenerationAutomationRepository;
import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.repository.IntegrationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentGenerationAutomationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Mock
    private DocumentGenerationAutomationRepository automationRepository;

    @Mock
    private IntegrationRepository integrationRepository;

    private DocumentGenerationAutomationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentGenerationAutomationService(
                automationRepository,
                integrationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returns_off_and_default_schedule_when_the_user_has_no_setting_row() {
        UUID userId = UUID.randomUUID();
        when(automationRepository.findByUserId(userId)).thenReturn(Optional.empty());

        DocumentGenerationAutomationResponse response = service.get(userId);

        assertThat(response.enabled()).isFalse();
        assertThat(response.schedule().dayOfWeek()).isEqualTo("MONDAY");
        assertThat(response.schedule().time()).isEqualTo("21:00");
        assertThat(response.schedule().timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void returns_the_saved_schedule_when_the_user_has_a_setting_row() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationAutomation setting =
                DocumentGenerationAutomation.create(userId, true, "THURSDAY", "15:00", NOW);
        when(automationRepository.findByUserId(userId)).thenReturn(Optional.of(setting));

        DocumentGenerationAutomationResponse response = service.get(userId);

        assertThat(response.enabled()).isTrue();
        assertThat(response.schedule().dayOfWeek()).isEqualTo("THURSDAY");
        assertThat(response.schedule().time()).isEqualTo("15:00");
    }

    @Test
    void rejects_enabling_when_github_is_not_connected() {
        UUID userId = UUID.randomUUID();
        when(integrationRepository.findWithLockByUserIdAndProvider(userId, IntegrationProvider.GITHUB))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, true, "MONDAY", "21:00"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GITHUB_INTEGRATION_REQUIRED);
    }

    @Test
    void enables_automation_when_github_is_connected() {
        UUID userId = UUID.randomUUID();
        Integration github = Integration.connecting(userId, IntegrationProvider.GITHUB);
        github.complete("encrypted-access-token", null, null, NOW);
        when(integrationRepository.findWithLockByUserIdAndProvider(userId, IntegrationProvider.GITHUB))
                .thenReturn(Optional.of(github));
        when(automationRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(automationRepository.save(any(DocumentGenerationAutomation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentGenerationAutomationResponse response = service.update(userId, true, "WEDNESDAY", "09:00");

        assertThat(response.enabled()).isTrue();
        assertThat(response.schedule().dayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(response.schedule().time()).isEqualTo("09:00");
        ArgumentCaptor<DocumentGenerationAutomation> captor = ArgumentCaptor.forClass(DocumentGenerationAutomation.class);
        verify(automationRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getDayOfWeek()).isEqualTo("WEDNESDAY");
        assertThat(captor.getValue().getTime()).isEqualTo("09:00");
    }

    @Test
    void updates_the_schedule_of_an_existing_setting() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationAutomation setting =
                DocumentGenerationAutomation.create(userId, false, "MONDAY", "21:00", NOW);
        when(automationRepository.findByUserId(userId)).thenReturn(Optional.of(setting));
        when(automationRepository.save(any(DocumentGenerationAutomation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentGenerationAutomationResponse response = service.update(userId, false, "FRIDAY", "18:30");

        assertThat(response.schedule().dayOfWeek()).isEqualTo("FRIDAY");
        assertThat(response.schedule().time()).isEqualTo("18:30");
        assertThat(setting.getDayOfWeek()).isEqualTo("FRIDAY");
        assertThat(setting.getTime()).isEqualTo("18:30");
    }

    @Test
    void enabled_only_update_keeps_the_previously_saved_schedule() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationAutomation setting =
                DocumentGenerationAutomation.create(userId, false, "SATURDAY", "08:00", NOW);
        when(automationRepository.findByUserId(userId)).thenReturn(Optional.of(setting));
        when(automationRepository.save(any(DocumentGenerationAutomation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentGenerationAutomationResponse response = service.update(userId, false);

        assertThat(response.schedule().dayOfWeek()).isEqualTo("SATURDAY");
        assertThat(response.schedule().time()).isEqualTo("08:00");
    }

    @Test
    void rejects_an_invalid_day_of_week() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.update(userId, false, "FUNDAY", "21:00"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void rejects_an_invalid_time_format() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.update(userId, false, "MONDAY", "9:00"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
    }

    @Test
    void disables_an_existing_setting_when_github_is_disconnected() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationAutomation setting = DocumentGenerationAutomation.create(userId, true, NOW);
        when(automationRepository.findByUserId(userId)).thenReturn(Optional.of(setting));

        service.disableForGithubDisconnect(userId);

        assertThat(setting.isEnabled()).isFalse();
        verify(automationRepository).save(setting);
    }
}
