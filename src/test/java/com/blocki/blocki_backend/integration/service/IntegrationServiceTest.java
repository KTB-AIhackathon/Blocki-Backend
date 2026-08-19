package com.blocki.blocki_backend.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.blocki.blocki_backend.integration.client.notion.NotionOAuthClient;
import com.blocki.blocki_backend.integration.client.notion.NotionOAuthClientException;
import com.blocki.blocki_backend.integration.client.notion.NotionTokenResponse;
import com.blocki.blocki_backend.integration.client.github.GithubOAuthClient;
import com.blocki.blocki_backend.integration.client.github.GithubTokenResponse;
import com.blocki.blocki_backend.integration.entity.Integration;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import com.blocki.blocki_backend.integration.entity.OAuthState;
import com.blocki.blocki_backend.integration.repository.IntegrationRepository;
import com.blocki.blocki_backend.integration.repository.OAuthStateRepository;
import com.blocki.blocki_backend.integration.security.OAuthStateGenerator;
import com.blocki.blocki_backend.integration.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T09:00:00Z");
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock
    private IntegrationRepository integrationRepository;

    @Mock
    private OAuthStateRepository oauthStateRepository;

    @Mock
    private NotionOAuthClient notionOAuthClient;

    @Mock
    private GithubOAuthClient githubOAuthClient;

    private final OAuthStateGenerator stateGenerator = new OAuthStateGenerator();
    private final TokenEncryptor tokenEncryptor = new TokenEncryptor(ENCRYPTION_KEY);
    private IntegrationService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationService(
                integrationRepository,
                oauthStateRepository,
                notionOAuthClient,
                githubOAuthClient,
                stateGenerator,
                tokenEncryptor,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejects_an_unknown_state_without_exchanging_the_code() {
        String stateHash = stateGenerator.hash("unknown-state");
        when(oauthStateRepository.consumeIfValid(stateHash, IntegrationProvider.NOTION, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.completeAuthorization(IntegrationProvider.NOTION, "code", "unknown-state"))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("OAUTH_STATE_INVALID");

        verifyNoInteractions(notionOAuthClient);
        verifyNoInteractions(integrationRepository);
    }

    @Test
    void consumes_a_valid_state_and_persists_only_an_encrypted_notion_access_token() {
        UUID userId = UUID.randomUUID();
        String rawState = "valid-state";
        OAuthState state = OAuthState.issue(
                userId,
                IntegrationProvider.NOTION,
                stateGenerator.hash(rawState),
                NOW.plusSeconds(600));
        Integration integration = Integration.connecting(userId, IntegrationProvider.NOTION);
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW))
                .thenReturn(1);
        when(oauthStateRepository.findByStateHash(stateGenerator.hash(rawState))).thenReturn(Optional.of(state));
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of(integration));
        when(notionOAuthClient.exchangeCode("authorization-code"))
                .thenReturn(new NotionTokenResponse(
                        "notion-access-token",
                        "bearer",
                        "notion-refresh-token",
                        "bot-id",
                        "workspace-id",
                        "Blocki Workspace"));

        service.completeAuthorization(IntegrationProvider.NOTION, "authorization-code", rawState);

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.CONNECTED);
        assertThat(integration.getAccountLabel()).isEqualTo("Blocki Workspace");
        assertThat(integration.getEncryptedAccessToken()).isNotEqualTo("notion-access-token");
        assertThat(tokenEncryptor.decrypt(integration.getEncryptedAccessToken())).isEqualTo("notion-access-token");
        assertThat(integration.getEncryptedRefreshToken()).isNotEqualTo("notion-refresh-token");
        assertThat(tokenEncryptor.decrypt(integration.getEncryptedRefreshToken())).isEqualTo("notion-refresh-token");
        InOrder stateThenExchange = inOrder(oauthStateRepository, notionOAuthClient);
        stateThenExchange.verify(oauthStateRepository)
                .consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW);
        stateThenExchange.verify(notionOAuthClient).exchangeCode("authorization-code");
        verify(integrationRepository).save(integration);
    }

    @Test
    void rejects_a_state_at_its_expiration_without_exchanging_the_code() {
        String rawState = "expired-state";
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW))
                .thenReturn(0);

        assertThatThrownBy(() -> service.completeAuthorization(IntegrationProvider.NOTION, "code", rawState))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("OAUTH_STATE_INVALID");

        verifyNoInteractions(notionOAuthClient);
        verifyNoInteractions(integrationRepository);
    }

    @Test
    void rejects_a_blank_authorization_code_before_consuming_the_state() {
        String rawState = "denied-state";

        assertThatThrownBy(() -> service.completeAuthorization(IntegrationProvider.NOTION, "   ", rawState))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("OAUTH_STATE_INVALID");

        verifyNoInteractions(oauthStateRepository);
        verifyNoInteractions(notionOAuthClient);
        verifyNoInteractions(integrationRepository);
    }

    @Test
    void rejects_an_already_consumed_state_without_exchanging_the_code() {
        String rawState = "consumed-state";
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW))
                .thenReturn(0);

        assertThatThrownBy(() -> service.completeAuthorization(IntegrationProvider.NOTION, "code", rawState))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("OAUTH_STATE_INVALID");

        verifyNoInteractions(notionOAuthClient);
        verifyNoInteractions(integrationRepository);
    }

    @Test
    void rejects_a_github_state_when_notion_only_atomic_consumption_affects_no_row() {
        String rawState = "github-state";
        OAuthState githubState = OAuthState.issue(
                UUID.randomUUID(),
                IntegrationProvider.GITHUB,
                stateGenerator.hash(rawState),
                NOW.plusSeconds(600));
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW))
                .thenReturn(0);

        assertThatThrownBy(() -> service.completeAuthorization(IntegrationProvider.NOTION, "code", rawState))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("OAUTH_STATE_INVALID");

        assertThat(githubState.getProvider()).isEqualTo(IntegrationProvider.GITHUB);
        verify(oauthStateRepository).consumeIfValid(
                stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW);
        verifyNoInteractions(notionOAuthClient);
        verifyNoInteractions(integrationRepository);
    }

    @Test
    void rejects_authorization_start_for_an_already_connected_user() {
        UUID userId = UUID.randomUUID();
        Integration integration = Integration.connecting(userId, IntegrationProvider.NOTION);
        integration.complete("existing-encrypted-token", "existing-encrypted-refresh-token", "Existing Workspace", NOW.minusSeconds(60));
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of(integration));

        assertThatThrownBy(() -> service.startAuthorization(userId, IntegrationProvider.NOTION))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("INTEGRATION_ALREADY_CONNECTED");

        verify(oauthStateRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preserves_existing_connection_when_notion_token_exchange_fails() {
        UUID userId = UUID.randomUUID();
        String rawState = "reconnect-state";
        OAuthState state = OAuthState.issue(
                userId,
                IntegrationProvider.NOTION,
                stateGenerator.hash(rawState),
                NOW.plusSeconds(600));
        Integration integration = Integration.connecting(userId, IntegrationProvider.NOTION);
        integration.complete("existing-encrypted-token", "existing-encrypted-refresh-token", "Existing Workspace", NOW.minusSeconds(60));
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.NOTION, NOW))
                .thenReturn(1);
        when(oauthStateRepository.findByStateHash(stateGenerator.hash(rawState))).thenReturn(Optional.of(state));
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of(integration));
        when(notionOAuthClient.exchangeCode("authorization-code"))
                .thenThrow(new NotionOAuthClientException(new RuntimeException("provider unavailable")));

        assertThatThrownBy(() -> service.completeAuthorization(IntegrationProvider.NOTION, "authorization-code", rawState))
                .isInstanceOf(IntegrationException.class)
                .extracting(exception -> ((IntegrationException) exception).getCode())
                .isEqualTo("EXTERNAL_SOURCE_FAILED");

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.CONNECTED);
        assertThat(integration.getEncryptedAccessToken()).isEqualTo("existing-encrypted-token");
        assertThat(integration.getAccountLabel()).isEqualTo("Existing Workspace");
    }

    @Test
    void completes_github_authorization_without_calling_github_profile_api() {
        UUID userId = UUID.randomUUID();
        String rawState = "github-state";
        OAuthState state = OAuthState.issue(
                userId,
                IntegrationProvider.GITHUB,
                stateGenerator.hash(rawState),
                NOW.plusSeconds(600));
        Integration integration = Integration.connecting(userId, IntegrationProvider.GITHUB);
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.GITHUB, NOW))
                .thenReturn(1);
        when(oauthStateRepository.findByStateHash(stateGenerator.hash(rawState))).thenReturn(Optional.of(state));
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.GITHUB))
                .thenReturn(Optional.of(integration));
        when(githubOAuthClient.exchangeCode("authorization-code"))
                .thenReturn(new GithubTokenResponse("github-access-token", "bearer", "read:user"));

        service.completeAuthorization(IntegrationProvider.GITHUB, "authorization-code", rawState);

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.CONNECTED);
        assertThat(integration.getAccountLabel()).isNull();
        assertThat(tokenEncryptor.decrypt(integration.getEncryptedAccessToken())).isEqualTo("github-access-token");
        assertThat(integration.getEncryptedRefreshToken()).isNull();
    }

    @Test
    void provides_a_connected_users_decrypted_token_only_to_an_internal_caller() {
        UUID userId = UUID.randomUUID();
        Integration integration = Integration.connecting(userId, IntegrationProvider.GITHUB);
        integration.complete(tokenEncryptor.encrypt("github-access-token"), null, null, NOW);
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.GITHUB))
                .thenReturn(Optional.of(integration));

        Optional<String> accessToken = service.findAccessToken(userId, IntegrationProvider.GITHUB);

        assertThat(accessToken).contains("github-access-token");
    }

    @Test
    void does_not_provide_a_token_for_a_provider_that_is_not_connected() {
        UUID userId = UUID.randomUUID();
        Integration integration = Integration.connecting(userId, IntegrationProvider.NOTION);
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of(integration));

        Optional<String> accessToken = service.findAccessToken(userId, IntegrationProvider.NOTION);

        assertThat(accessToken).isEmpty();
    }

    @Test
    void lists_notion_connection_and_github_as_not_connected_without_sensitive_fields() {
        UUID userId = UUID.randomUUID();
        Integration integration = Integration.connecting(userId, IntegrationProvider.NOTION);
        integration.complete("encrypted-token", "encrypted-refresh-token", "Blocki Workspace", NOW);
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of(integration));

        List<IntegrationResult> results = service.listIntegrations(userId);

        assertThat(results).containsExactly(
                new IntegrationResult(
                        IntegrationProvider.NOTION,
                        IntegrationStatus.CONNECTED,
                        "Blocki Workspace",
                        NOW,
                        null),
                new IntegrationResult(
                        IntegrationProvider.GITHUB,
                        IntegrationStatus.NOT_CONNECTED,
                        null,
                        null,
                        null));
    }

    @Test
    void marks_a_cancelled_provider_authorization_as_an_error_after_consuming_its_state() {
        UUID userId = UUID.randomUUID();
        String rawState = "cancelled-state";
        OAuthState state = OAuthState.issue(
                userId,
                IntegrationProvider.GITHUB,
                stateGenerator.hash(rawState),
                NOW.plusSeconds(600));
        Integration integration = Integration.connecting(userId, IntegrationProvider.GITHUB);
        when(oauthStateRepository.consumeIfValid(stateGenerator.hash(rawState), IntegrationProvider.GITHUB, NOW))
                .thenReturn(1);
        when(oauthStateRepository.findByStateHash(stateGenerator.hash(rawState))).thenReturn(Optional.of(state));
        when(integrationRepository.findByUserIdAndProvider(userId, IntegrationProvider.GITHUB))
                .thenReturn(Optional.of(integration));

        service.cancelAuthorization(IntegrationProvider.GITHUB, rawState);

        assertThat(integration.getStatus()).isEqualTo(IntegrationStatus.ERROR);
        assertThat(integration.getErrorCode()).isEqualTo(IntegrationException.OAUTH_AUTHORIZATION_DENIED);
    }
}
