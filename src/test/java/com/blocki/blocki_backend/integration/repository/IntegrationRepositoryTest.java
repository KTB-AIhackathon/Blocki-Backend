package com.blocki.blocki_backend.integration.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class IntegrationRepositoryTest {

    @Autowired
    private IntegrationRepository repository;

    @Test
    void rejects_second_notion_connection_for_same_user() {
        UUID userId = UUID.randomUUID();
        repository.save(Integration.connecting(userId, IntegrationProvider.NOTION));

        assertThatThrownBy(() -> repository.saveAndFlush(
                Integration.connecting(userId, IntegrationProvider.NOTION)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
