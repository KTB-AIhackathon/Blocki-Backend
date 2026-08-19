package com.blocki.blocki_backend.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OAuthStateGeneratorTest {

    private final OAuthStateGenerator generator = new OAuthStateGenerator();

    @Test
    void generates_url_safe_state_without_padding() {
        String state = generator.generate();

        assertThat(state).matches("[A-Za-z0-9_-]+");
        assertThat(state).doesNotContain("=");
    }

    @Test
    void hashes_identical_raw_states_deterministically() {
        String firstHash = generator.hash("test-state-input");
        String secondHash = generator.hash("test-state-input");

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(generator.hash("different-test-state-input")).isNotEqualTo(firstHash);
    }
}
