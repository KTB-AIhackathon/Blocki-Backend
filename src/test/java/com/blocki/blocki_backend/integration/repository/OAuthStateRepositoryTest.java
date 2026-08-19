package com.blocki.blocki_backend.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.OAuthState;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
class OAuthStateRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-19T09:00:00Z");

    @Autowired
    private OAuthStateRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void commits_valid_state_consumption_even_when_the_calling_transaction_rolls_back() {
        String stateHash = "state-hash";
        repository.saveAndFlush(OAuthState.issue(
                UUID.randomUUID(), IntegrationProvider.NOTION, stateHash, NOW.plusSeconds(600)));

        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        outerTransaction.executeWithoutResult(status -> {
            assertThat(repository.consumeIfValid(stateHash, IntegrationProvider.NOTION, NOW)).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(repository.findByStateHash(stateHash).orElseThrow().getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void does_not_consume_a_github_state_when_the_callback_requires_notion() {
        String stateHash = "github-state-hash";
        repository.saveAndFlush(OAuthState.issue(
                UUID.randomUUID(), IntegrationProvider.GITHUB, stateHash, NOW.plusSeconds(600)));

        assertThat(repository.consumeIfValid(stateHash, IntegrationProvider.NOTION, NOW)).isZero();

        assertThat(repository.findByStateHash(stateHash).orElseThrow().getConsumedAt()).isNull();
    }
}
