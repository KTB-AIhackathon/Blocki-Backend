package com.blocki.blocki_backend.integration.client.github;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope
) {
}
