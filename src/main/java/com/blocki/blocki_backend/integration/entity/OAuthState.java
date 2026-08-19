package com.blocki.blocki_backend.integration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class OAuthState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationProvider provider;

    @Column(name = "state_hash", nullable = false, unique = true, length = 128)
    private String stateHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected OAuthState() {
    }

    private OAuthState(UUID userId, IntegrationProvider provider, String stateHash, Instant expiresAt) {
        this.userId = userId;
        this.provider = provider;
        this.stateHash = stateHash;
        this.expiresAt = expiresAt;
    }

    public static OAuthState issue(
            UUID userId, IntegrationProvider provider, String stateHash, Instant expiresAt) {
        return new OAuthState(userId, provider, stateHash, expiresAt);
    }

    public boolean isUnconsumedAndValidAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
