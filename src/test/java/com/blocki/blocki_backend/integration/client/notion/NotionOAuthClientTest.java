package com.blocki.blocki_backend.integration.client.notion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.blocki.blocki_backend.integration.config.NotionOAuthProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class NotionOAuthClientTest {

    private static final String CLIENT_ID = "client-id";
    private static final String CLIENT_SECRET = "client-secret";
    private static final String REDIRECT_URI = "https://blocki.example.com/api/v1/integrations/notion/callback";

    private MockRestServiceServer server;
    private NotionOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NotionOAuthClient(builder.build(), properties());
    }

    @Test
    void builds_an_https_authorization_uri_with_required_oauth_parameters() {
        String uri = client.buildAuthorizeUri("state-value").toString();

        assertThat(uri).startsWith("https://api.notion.com/v1/oauth/authorize?");
        assertThat(uri).contains("client_id=client-id");
        assertThat(uri).contains("redirect_uri=https://blocki.example.com/api/v1/integrations/notion/callback");
        assertThat(uri).contains("owner=user");
        assertThat(uri).contains("response_type=code");
        assertThat(uri).contains("state=state-value");
    }

    @Test
    void exchanges_an_authorization_code_with_notion_and_maps_the_token_response() {
        server.expect(once(), requestTo("https://api.notion.com/v1/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(CONTENT_TYPE, APPLICATION_JSON.toString()))
                .andExpect(header(AUTHORIZATION, "Basic " + basicCredentials()))
                .andExpect(content().json("""
                        {
                          "grant_type": "authorization_code",
                          "code": "authorization-code",
                          "redirect_uri": "https://blocki.example.com/api/v1/integrations/notion/callback"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "access_token": "notion-access-token",
                          "token_type": "bearer",
                          "refresh_token": "notion-refresh-token",
                          "bot_id": "bot-id",
                          "workspace_id": "workspace-id",
                          "workspace_name": "Blocki Workspace"
                        }
                        """, APPLICATION_JSON));

        NotionTokenResponse response = client.exchangeCode("authorization-code");

        assertThat(response.accessToken()).isEqualTo("notion-access-token");
        assertThat(response.tokenType()).isEqualTo("bearer");
        assertThat(response.refreshToken()).isEqualTo("notion-refresh-token");
        assertThat(response.botId()).isEqualTo("bot-id");
        assertThat(response.workspaceId()).isEqualTo("workspace-id");
        assertThat(response.workspaceName()).isEqualTo("Blocki Workspace");
        server.verify();
    }

    @Test
    void maps_notion_non_success_responses_to_a_client_exception() {
        server.expect(requestTo("https://api.notion.com/v1/oauth/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.exchangeCode("authorization-code"))
                .isInstanceOf(NotionOAuthClientException.class);
        server.verify();
    }

    @Test
    void maps_transport_errors_to_a_client_exception() {
        NotionOAuthClient unavailableClient = new NotionOAuthClient(
                RestClient.builder().baseUrl("http://127.0.0.1:1").build(), properties());

        assertThatThrownBy(() -> unavailableClient.exchangeCode("authorization-code"))
                .isInstanceOf(NotionOAuthClientException.class);
    }

    @Test
    void a_local_stack_can_stand_a_fake_identity_provider_in_front() {
        NotionOAuthProperties properties = properties();
        properties.setAuthorizeUri("http://localhost:9100/notion/v1/oauth/authorize");
        properties.setTokenUri("http://oauth-provider:9100/notion/v1/oauth/token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer stub = MockRestServiceServer.bindTo(builder).build();
        NotionOAuthClient local = new NotionOAuthClient(builder.build(), properties);
        stub.expect(requestTo("http://oauth-provider:9100/notion/v1/oauth/token"))
                .andRespond(withSuccess("""
                        { "access_token": "stub-token", "token_type": "bearer", "workspace_name": "스텁" }
                        """, APPLICATION_JSON));

        assertThat(local.buildAuthorizeUri("s").toString())
                .startsWith("http://localhost:9100/notion/v1/oauth/authorize?");
        assertThat(local.exchangeCode("code").accessToken()).isEqualTo("stub-token");
        stub.verify();
    }

    private NotionOAuthProperties properties() {
        NotionOAuthProperties properties = new NotionOAuthProperties();
        properties.setClientId(CLIENT_ID);
        properties.setClientSecret(CLIENT_SECRET);
        properties.setRedirectUri(REDIRECT_URI);
        return properties;
    }

    private String basicCredentials() {
        return Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET)
                .getBytes(StandardCharsets.UTF_8));
    }
}
