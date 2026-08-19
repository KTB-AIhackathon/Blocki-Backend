package com.blocki.blocki_backend.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJobStatus;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.repository.DocumentGenerationJobRepository;
import com.blocki.blocki_backend.user.entity.User;
import com.blocki.blocki_backend.user.repository.UserRepository;
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
class DocumentGenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T09:00:00Z");

    @Mock
    private DocumentGenerationJobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    private DocumentGenerationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentGenerationService(jobRepository, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(userRepository.findWithLockById(any())).thenReturn(Optional.of(org.mockito.Mockito.mock(User.class)));
    }

    @Test
    void queues_a_document_generation_job() {
        UUID userId = UUID.randomUUID();
        when(jobRepository.findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(jobRepository.findByUserIdAndDocumentTypeAndStatusIn(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(jobRepository.save(any(DocumentGenerationJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DocumentGenerationResult result = service.request(userId, DocumentType.RESUME, "request-key");

        assertThat(result.type()).isEqualTo("DOCUMENT_GENERATION");
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.progress()).isZero();
        assertThat(result.attempt()).isEqualTo(1);
        assertThat(result.maxAttempts()).isEqualTo(3);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.missingSources()).isEmpty();
        ArgumentCaptor<DocumentGenerationJob> jobCaptor = ArgumentCaptor.forClass(DocumentGenerationJob.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getDocumentType()).isEqualTo(DocumentType.RESUME);
    }

    @Test
    void returns_the_original_job_for_an_idempotent_retry() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationJob existing = DocumentGenerationJob.queue(
                userId, DocumentType.RESUME, "request-key", NOW, NOW.plusSeconds(86_400));
        when(jobRepository.findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(userId, "request-key", NOW))
                .thenReturn(Optional.of(existing));

        DocumentGenerationResult result = service.request(userId, DocumentType.RESUME, "request-key");

        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.status()).isEqualTo("QUEUED");
    }

    @Test
    void rejects_reusing_a_key_for_a_different_document_type() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationJob existing = DocumentGenerationJob.queue(
                userId, DocumentType.RESUME, "request-key", NOW, NOW.plusSeconds(86_400));
        when(jobRepository.findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(userId, "request-key", NOW))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.request(userId, DocumentType.PORTFOLIO, "request-key"))
                .isInstanceOf(DocumentGenerationException.class)
                .extracting(exception -> ((DocumentGenerationException) exception).getCode())
                .isEqualTo(DocumentGenerationException.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void rejects_a_second_active_job_of_the_same_type() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationJob activeJob = DocumentGenerationJob.queue(
                userId, DocumentType.RESUME, "first-key", NOW, NOW.plusSeconds(86_400));
        when(jobRepository.findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(jobRepository.findByUserIdAndDocumentTypeAndStatusIn(
                userId,
                DocumentType.RESUME,
                java.util.List.of(DocumentGenerationJobStatus.QUEUED, DocumentGenerationJobStatus.RUNNING)))
                .thenReturn(Optional.of(activeJob));

        assertThatThrownBy(() -> service.request(userId, DocumentType.RESUME, "second-key"))
                .isInstanceOf(DocumentGenerationException.class)
                .extracting(exception -> ((DocumentGenerationException) exception).getCode())
                .isEqualTo(DocumentGenerationException.JOB_ALREADY_RUNNING);
    }

    @Test
    void removes_an_expired_key_before_creating_a_new_job() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationJob expired = DocumentGenerationJob.queue(
                userId, DocumentType.RESUME, "request-key", NOW.minusSeconds(86_401), NOW.minusSeconds(1));
        when(jobRepository.findByUserIdAndIdempotencyKeyAndIdempotencyExpiresAtAfter(userId, "request-key", NOW))
                .thenReturn(Optional.empty());
        when(jobRepository.findByUserIdAndIdempotencyKey(userId, "request-key")).thenReturn(Optional.of(expired));
        when(jobRepository.findByUserIdAndDocumentTypeAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());
        when(jobRepository.save(any(DocumentGenerationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.request(userId, DocumentType.RESUME, "request-key");

        verify(jobRepository).delete(expired);
    }
}
