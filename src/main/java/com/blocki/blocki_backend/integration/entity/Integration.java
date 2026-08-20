package com.blocki.blocki_backend.integration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "integrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_integrations_user_provider",
                columnNames = {"user_id", "provider"}))
public class Integration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationStatus status;

    @Column(name = "account_label", length = 100)
    private String accountLabel;

    @Column(name = "encrypted_access_token", length = 4096)
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", length = 4096)
    private String encryptedRefreshToken;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    protected Integration() {
    }

    private Integration(UUID userId, IntegrationProvider provider, IntegrationStatus status) {
        this.userId = userId;
        this.provider = provider;
        this.status = status;
    }

    public static Integration connecting(UUID userId, IntegrationProvider provider) {
        return new Integration(userId, provider, IntegrationStatus.CONNECTING);
    }

    public void beginAuthorization() {
        this.status = IntegrationStatus.CONNECTING;
        this.errorCode = null;
    }

    public void complete(
            String encryptedAccessToken,
            String encryptedRefreshToken,
            String accountLabel,
            Instant connectedAt) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.accountLabel = accountLabel;
        this.connectedAt = connectedAt;
        this.status = IntegrationStatus.CONNECTED;
        this.errorCode = null;
    }

    public void fail(String errorCode) {
        this.status = IntegrationStatus.ERROR;
        this.errorCode = errorCode;
    }

    public void disconnect() {
        this.encryptedAccessToken = null;
        this.encryptedRefreshToken = null;
        this.accountLabel = null;
        this.connectedAt = null;
        this.status = IntegrationStatus.NOT_CONNECTED;
        this.errorCode = null;
    }

    public IntegrationProvider getProvider() {
        return provider;
    }

    public UUID getUserId() {
        return userId;
    }

    public IntegrationStatus getStatus() {
        return status;
    }

    public String getAccountLabel() {
        return accountLabel;
    }

    public String getEncryptedAccessToken() {
        return encryptedAccessToken;
    }

    public String getEncryptedRefreshToken() {
        return encryptedRefreshToken;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
