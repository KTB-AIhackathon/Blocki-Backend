package com.blocki.blocki_backend.integration.client.github;

import com.blocki.blocki_backend.integration.config.GithubOAuthProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

public class GithubOAuthClient {

    private final RestClient restClient;
    private final GithubOAuthProperties properties;

    public GithubOAuthClient(RestClient restClient, GithubOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public URI buildAuthorizeUri(String state) {
        return UriComponentsBuilder.fromUriString(properties.getAuthorizeUri())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    public GithubTokenResponse exchangeCode(String code) {
        try {
            GithubTokenResponse response = restClient.post()
                    .uri(properties.getTokenUri())
                    .header("Accept", "application/json")
                    .body(Map.of(
                            "client_id", properties.getClientId(),
                            "client_secret", properties.getClientSecret(),
                            "code", code,
                            "redirect_uri", properties.getRedirectUri()))
                    .retrieve()
                    .body(GithubTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new GithubOAuthClientException("GitHub OAuth token response has no access token");
            }
            return response;
        } catch (RestClientException exception) {
            throw new GithubOAuthClientException(exception);
        }
    }

}
