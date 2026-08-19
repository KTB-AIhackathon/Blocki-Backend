package com.blocki.blocki_backend.integration.client.notion;

import com.blocki.blocki_backend.integration.config.NotionOAuthProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

public class NotionOAuthClient {

    private static final String AUTHORIZE_URI = "https://api.notion.com/v1/oauth/authorize";
    private static final String TOKEN_URI = "https://api.notion.com/v1/oauth/token";

    private final RestClient restClient;
    private final NotionOAuthProperties properties;

    public NotionOAuthClient(RestClient restClient, NotionOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public URI buildAuthorizeUri(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("owner", "user")
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    public NotionTokenResponse exchangeCode(String code) {
        try {
            return restClient.post()
                    .uri(TOKEN_URI)
                    .headers(headers -> headers.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                    .body(Map.of(
                            "grant_type", "authorization_code",
                            "code", code,
                            "redirect_uri", properties.getRedirectUri()))
                    .retrieve()
                    .body(NotionTokenResponse.class);
        } catch (RestClientException exception) {
            throw new NotionOAuthClientException(exception);
        }
    }
}
