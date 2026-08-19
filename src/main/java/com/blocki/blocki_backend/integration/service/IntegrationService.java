package com.blocki.blocki_backend.integration.service;

import com.blocki.blocki_backend.integration.client.github.GithubOAuthClient;
import com.blocki.blocki_backend.integration.client.github.GithubOAuthClientException;
import com.blocki.blocki_backend.integration.client.github.GithubTokenResponse;
import com.blocki.blocki_backend.integration.client.notion.NotionOAuthClient;
import com.blocki.blocki_backend.integration.client.notion.NotionOAuthClientException;
import com.blocki.blocki_backend.integration.client.notion.NotionTokenResponse;
import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import com.blocki.blocki_backend.integration.entity.OAuthState;
import com.blocki.blocki_backend.integration.repository.IntegrationRepository;
import com.blocki.blocki_backend.integration.repository.OAuthStateRepository;
import com.blocki.blocki_backend.integration.security.OAuthStateGenerator;
import com.blocki.blocki_backend.integration.security.TokenEncryptor;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class IntegrationService implements IntegrationTokenProvider {

    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);

    private final IntegrationRepository integrationRepository;
    private final OAuthStateRepository oauthStateRepository;
    private final NotionOAuthClient notionOAuthClient;
    private final GithubOAuthClient githubOAuthClient;
    private final OAuthStateGenerator stateGenerator;
    private final TokenEncryptor tokenEncryptor;
    private final Clock clock;

    public IntegrationService(
            IntegrationRepository integrationRepository,
            OAuthStateRepository oauthStateRepository,
            NotionOAuthClient notionOAuthClient,
            GithubOAuthClient githubOAuthClient,
            OAuthStateGenerator stateGenerator,
            TokenEncryptor tokenEncryptor,
            Clock clock) {
        this.integrationRepository = integrationRepository;
        this.oauthStateRepository = oauthStateRepository;
        this.notionOAuthClient = notionOAuthClient;
        this.githubOAuthClient = githubOAuthClient;
        this.stateGenerator = stateGenerator;
        this.tokenEncryptor = tokenEncryptor;
        this.clock = clock;
    }

    @Transactional
    public URI startAuthorization(UUID userId, IntegrationProvider provider) {
        Optional<Integration> existing = integrationRepository.findByUserIdAndProvider(userId, provider);
        if (existing.map(Integration::getStatus).filter(IntegrationStatus.CONNECTED::equals).isPresent()) {
            throw new IntegrationException(IntegrationException.INTEGRATION_ALREADY_CONNECTED);
        }

        Integration integration = existing.orElseGet(() -> Integration.connecting(userId, provider));
        integration.beginAuthorization();
        integrationRepository.save(integration);

        String rawState = stateGenerator.generate();
        Instant now = clock.instant();
        oauthStateRepository.save(OAuthState.issue(
                userId,
                provider,
                stateGenerator.hash(rawState),
                now.plus(OAUTH_STATE_TTL)));
        return switch (provider) {
            case NOTION -> notionOAuthClient.buildAuthorizeUri(rawState);
            case GITHUB -> githubOAuthClient.buildAuthorizeUri(rawState);
        };
    }

    @Transactional(noRollbackFor = IntegrationException.class)
    public void completeAuthorization(IntegrationProvider provider, String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new IntegrationException(IntegrationException.OAUTH_STATE_INVALID);
        }

        Instant now = clock.instant();
        String stateHash = stateGenerator.hash(state);
        if (oauthStateRepository.consumeIfValid(stateHash, provider, now) != 1) {
            throw new IntegrationException(IntegrationException.OAUTH_STATE_INVALID);
        }

        OAuthState oauthState = oauthStateRepository.findByStateHash(stateHash)
                .orElseThrow(() -> new IntegrationException(IntegrationException.OAUTH_STATE_INVALID));

        Integration integration = integrationRepository
                .findByUserIdAndProvider(oauthState.getUserId(), provider)
                .orElseThrow(() -> new IntegrationException(IntegrationException.OAUTH_STATE_INVALID));
        try {
            OAuthCompletion completion = exchangeCode(provider, code);
            integration.complete(
                    tokenEncryptor.encrypt(completion.accessToken()),
                    completion.refreshToken() == null ? null : tokenEncryptor.encrypt(completion.refreshToken()),
                    completion.accountLabel(),
                    now);
            integrationRepository.save(integration);
        } catch (NotionOAuthClientException | GithubOAuthClientException exception) {
            if (integration.getStatus() != IntegrationStatus.CONNECTED) {
                integration.fail(IntegrationException.EXTERNAL_SOURCE_FAILED);
                integrationRepository.save(integration);
            }
            throw new IntegrationException(IntegrationException.EXTERNAL_SOURCE_FAILED, exception);
        }
    }

    @Transactional
    public void cancelAuthorization(IntegrationProvider provider, String state) {
        if (state == null || state.isBlank()) {
            throw new IntegrationException(IntegrationException.OAUTH_STATE_INVALID);
        }

        Instant now = clock.instant();
        String stateHash = stateGenerator.hash(state);
        if (oauthStateRepository.consumeIfValid(stateHash, provider, now) != 1) {
            throw new IntegrationException(IntegrationException.OAUTH_STATE_INVALID);
        }
        OAuthState oauthState = oauthStateRepository.findByStateHash(stateHash)
                .orElseThrow(() -> new IntegrationException(IntegrationException.OAUTH_STATE_INVALID));
        integrationRepository.findByUserIdAndProvider(oauthState.getUserId(), provider)
                .ifPresent(integration -> {
                    integration.fail(IntegrationException.OAUTH_AUTHORIZATION_DENIED);
                    integrationRepository.save(integration);
                });
    }

    public List<IntegrationResult> listIntegrations(UUID userId) {
        return List.of(IntegrationProvider.NOTION, IntegrationProvider.GITHUB).stream()
                .map(provider -> integrationRepository.findByUserIdAndProvider(userId, provider)
                        .map(this::toResult)
                        .orElseGet(() -> notConnected(provider)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findAccessToken(UUID userId, IntegrationProvider provider) {
        return integrationRepository.findByUserIdAndProvider(userId, provider)
                .filter(integration -> integration.getStatus() == IntegrationStatus.CONNECTED)
                .map(Integration::getEncryptedAccessToken)
                .filter(accessToken -> !accessToken.isBlank())
                .map(tokenEncryptor::decrypt);
    }

    private OAuthCompletion exchangeCode(IntegrationProvider provider, String code) {
        return switch (provider) {
            case NOTION -> {
                NotionTokenResponse response = notionOAuthClient.exchangeCode(code);
                yield new OAuthCompletion(response.accessToken(), response.refreshToken(), response.workspaceName());
            }
            case GITHUB -> {
                GithubTokenResponse response = githubOAuthClient.exchangeCode(code);
                yield new OAuthCompletion(response.accessToken(), null, null);
            }
        };
    }

    private IntegrationResult toResult(Integration integration) {
        return new IntegrationResult(
                integration.getProvider(),
                integration.getStatus(),
                integration.getAccountLabel(),
                integration.getConnectedAt(),
                integration.getErrorCode());
    }

    private IntegrationResult notConnected(IntegrationProvider provider) {
        return new IntegrationResult(provider, IntegrationStatus.NOT_CONNECTED, null, null, null);
    }

    private record OAuthCompletion(String accessToken, String refreshToken, String accountLabel) {
    }
}
