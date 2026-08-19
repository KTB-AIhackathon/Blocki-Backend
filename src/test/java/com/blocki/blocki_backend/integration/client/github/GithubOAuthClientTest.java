package com.blocki.blocki_backend.integration.client.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.blocki.blocki_backend.integration.config.GithubOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubOAuthClientTest {

    private MockRestServiceServer server;
    private GithubOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubOAuthClient(builder.build(), properties());
    }

    @Test
    void builds_authorization_uri_with_the_configured_least_privilege_scope() {
        String uri = client.buildAuthorizeUri("state-value").toString();

        assertThat(uri).startsWith("https://github.com/login/oauth/authorize?");
        assertThat(uri).contains("client_id=client-id");
        assertThat(uri).contains("redirect_uri=https://blocki.example.com/api/v1/integrations/github/callback");
        assertThat(uri).contains("scope=read:user");
        assertThat(uri).contains("state=state-value");
    }

    @Test
    void exchanges_code_without_calling_the_github_data_api() {
        server.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Accept", APPLICATION_JSON.toString()))
                .andExpect(header(CONTENT_TYPE, APPLICATION_JSON.toString()))
                .andExpect(content().json("""
                        {
                          "client_id": "client-id",
                          "client_secret": "client-secret",
                          "code": "authorization-code",
                          "redirect_uri": "https://blocki.example.com/api/v1/integrations/github/callback"
                        }
                        """))
                .andRespond(withSuccess("""
                        { "access_token": "github-access-token", "token_type": "bearer", "scope": "read:user" }
                        """, APPLICATION_JSON));
        GithubTokenResponse token = client.exchangeCode("authorization-code");

        assertThat(token.accessToken()).isEqualTo("github-access-token");
        server.verify();
    }

    private GithubOAuthProperties properties() {
        GithubOAuthProperties properties = new GithubOAuthProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://blocki.example.com/api/v1/integrations/github/callback");
        return properties;
    }
}
