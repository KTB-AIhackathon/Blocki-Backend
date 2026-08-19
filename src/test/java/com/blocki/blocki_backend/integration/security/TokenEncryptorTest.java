package com.blocki.blocki_backend.integration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenEncryptorTest {

    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encrypts_with_a_fresh_nonce_and_decrypts_the_original_value() {
        TokenEncryptor encryptor = new TokenEncryptor(ENCRYPTION_KEY);

        String first = encryptor.encrypt("token-value");
        String second = encryptor.encrypt("token-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("token-value");
    }

    @Test
    void rejects_a_key_that_does_not_decode_to_32_bytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatIllegalArgumentException().isThrownBy(() -> new TokenEncryptor(shortKey));
    }

    @Test
    void rejects_a_null_encryption_key() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TokenEncryptor(null));
    }

    @Test
    void rejects_malformed_or_too_short_ciphertext() {
        TokenEncryptor encryptor = new TokenEncryptor(ENCRYPTION_KEY);
        String nonceWithoutAuthenticationTag = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[12]);

        assertThatIllegalArgumentException().isThrownBy(() -> encryptor.decrypt("not valid base64"));
        assertThatIllegalArgumentException().isThrownBy(() -> encryptor.decrypt(nonceWithoutAuthenticationTag));
    }

    @Test
    void rejects_a_null_ciphertext() {
        TokenEncryptor encryptor = new TokenEncryptor(ENCRYPTION_KEY);

        assertThatIllegalArgumentException().isThrownBy(() -> encryptor.decrypt(null));
    }
}
