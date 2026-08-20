package com.blocki.blocki_backend.document.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.document.entity.DocumentGenerationAutomation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class DocumentGenerationAutomationRepositoryTest {

    @Autowired
    private DocumentGenerationAutomationRepository repository;

    @Test
    void finds_only_enabled_automation_settings() {
        UUID enabledUserId = UUID.randomUUID();
        UUID disabledUserId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-20T12:00:00Z");
        repository.save(DocumentGenerationAutomation.create(enabledUserId, true, now));
        repository.save(DocumentGenerationAutomation.create(disabledUserId, false, now));

        assertThat(repository.findByEnabledTrue())
                .extracting(DocumentGenerationAutomation::getUserId)
                .containsExactly(enabledUserId);
    }

}
