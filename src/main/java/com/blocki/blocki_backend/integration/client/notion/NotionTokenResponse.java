package com.blocki.blocki_backend.integration.client.notion;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotionTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("bot_id") String botId,
        @JsonProperty("workspace_id") String workspaceId,
        @JsonProperty("workspace_name") String workspaceName
) {
}
