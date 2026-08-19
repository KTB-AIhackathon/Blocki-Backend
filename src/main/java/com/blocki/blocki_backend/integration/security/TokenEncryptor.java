package com.blocki.blocki_backend.integration.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "INTEGRATION_TOKEN_ENCRYPTION_KEY")
public class TokenEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_BYTE_LENGTH = 32;
    private static final int NONCE_BYTE_LENGTH = 12;
    private static final int TAG_BIT_LENGTH = 128;
    private static final int TAG_BYTE_LENGTH = TAG_BIT_LENGTH / Byte.SIZE;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenEncryptor(@Value("${INTEGRATION_TOKEN_ENCRYPTION_KEY}") String base64Key) {
        this.key = new SecretKeySpec(decodeKey(base64Key), "AES");
    }

    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt token", exception);
        }
    }

    public String decrypt(String encodedPayload) {
        byte[] payload = decodePayload(encodedPayload);
        byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, NONCE_BYTE_LENGTH, payload.length);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BIT_LENGTH, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Ciphertext cannot be decrypted", exception);
        }
    }

    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null) {
            throw new IllegalArgumentException("Encryption key must be Base64-encoded");
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Encryption key must be Base64-encoded", exception);
        }

        if (decodedKey.length != KEY_BYTE_LENGTH) {
            throw new IllegalArgumentException("Encryption key must decode to 32 bytes");
        }
        return decodedKey;
    }

    private static byte[] decodePayload(String encodedPayload) {
        if (encodedPayload == null) {
            throw new IllegalArgumentException("Ciphertext must be URL-safe Base64");
        }

        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(encodedPayload);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Ciphertext must be URL-safe Base64", exception);
        }

        if (payload.length < NONCE_BYTE_LENGTH + TAG_BYTE_LENGTH) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }
        return payload;
    }
}
