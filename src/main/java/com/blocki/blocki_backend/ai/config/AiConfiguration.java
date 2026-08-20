package com.blocki.blocki_backend.ai.config;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.ai.client.NotionDashboardResolver;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.integration.service.NotionConnectHook;
import com.blocki.blocki_backend.integration.service.NotionDashboardStore;
import com.blocki.blocki_backend.user.repository.UserRepository;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = {"ai.base-url", "ai.internal-key"})
public class AiConfiguration {

    /** Finding or building the dashboard is a handful of Notion writes, not a generation run. */
    private static final Duration DASHBOARD_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    RestClient aiRestClient(AiProperties properties) {
        return restClient(properties, Duration.ofSeconds(properties.getTimeoutSeconds()));
    }

    @Bean
    NotionDashboardResolver notionDashboardResolver(
            AiProperties properties,
            IntegrationTokenProvider integrationTokenProvider,
            NotionDashboardStore notionDashboardStore) {
        return new NotionDashboardResolver(
                restClient(properties, DASHBOARD_TIMEOUT),
                properties,
                integrationTokenProvider,
                notionDashboardStore);
    }

    @Bean
    NotionConnectHook notionConnectHook(NotionDashboardResolver notionDashboardResolver) {
        return notionDashboardResolver::resolveParentPageId;
    }

    @Bean
    DocumentGenerationClient documentGenerationClient(
            RestClient aiRestClient,
            AiProperties properties,
            IntegrationTokenProvider integrationTokenProvider,
            NotionDashboardResolver notionDashboardResolver,
            UserRepository userRepository,
            JsonMapper jsonMapper) {
        return new DocumentGenerationClient(
                aiRestClient,
                properties,
                integrationTokenProvider,
                notionDashboardResolver,
                userRepository,
                jsonMapper);
    }

    private static RestClient restClient(AiProperties properties, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
