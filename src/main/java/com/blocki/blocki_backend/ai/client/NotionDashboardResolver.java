package com.blocki.blocki_backend.ai.client;

import com.blocki.blocki_backend.ai.config.AiProperties;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.integration.service.NotionDashboardStore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Finds the one Notion page a user's generated documents belong under.
 *
 * <p>Resolved when a job needs it rather than at OAuth time, so a worker that was
 * down during the callback does not leave the account permanently unable to
 * publish. Notion is optional throughout: every failure here returns empty and the
 * document is still written to our own database.
 */
public class NotionDashboardResolver {

    private static final Logger log = LoggerFactory.getLogger(NotionDashboardResolver.class);

    private final RestClient restClient;
    private final AiProperties properties;
    private final IntegrationTokenProvider tokenProvider;
    private final NotionDashboardStore dashboardStore;

    public NotionDashboardResolver(
            RestClient restClient,
            AiProperties properties,
            IntegrationTokenProvider tokenProvider,
            NotionDashboardStore dashboardStore) {
        this.restClient = restClient;
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        this.dashboardStore = dashboardStore;
    }

    /** The user's Notion token, or empty when they have not connected Notion. */
    public Optional<String> findAccessToken(UUID userId) {
        return tokenProvider.findAccessToken(userId, IntegrationProvider.NOTION);
    }

    /**
     * The dashboard page id to send as {@code notion.parent_id}, creating the page
     * on the user's behalf the first time.
     */
    public Optional<String> resolveParentPageId(UUID userId, String notionToken) {
        String knownPageId = dashboardStore.findNotionDashboardPageId(userId).orElse(null);
        EnsureResponse response;
        try {
            response = restClient.post()
                    .uri("/internal/notion/dashboard")
                    .header("X-Internal-Key", properties.getInternalKey())
                    .header("X-Notion-Token", notionToken)
                    .body(new EnsureRequest(userId.toString(), knownPageId))
                    .retrieve()
                    .body(EnsureResponse.class);
        } catch (RestClientException exception) {
            log.warn("Notion dashboard lookup failed for user {}", userId, exception);
            return Optional.ofNullable(knownPageId);
        }
        if (response == null || !response.ok() || response.pageId() == null || response.pageId().isBlank()) {
            log.warn("Notion dashboard unavailable for user {}", userId);
            return Optional.ofNullable(knownPageId);
        }
        if (!response.pageId().equals(knownPageId)) {
            dashboardStore.rememberNotionDashboardPageId(userId, response.pageId());
        }
        return Optional.of(response.pageId());
    }

    private record EnsureRequest(
            @JsonProperty("user_id") String userId,
            @JsonProperty("known_page_id") String knownPageId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnsureResponse(
            @JsonProperty("ok") boolean ok,
            @JsonProperty("page_id") String pageId,
            @JsonProperty("page_url") String pageUrl,
            @JsonProperty("created") boolean created) {
    }
}
