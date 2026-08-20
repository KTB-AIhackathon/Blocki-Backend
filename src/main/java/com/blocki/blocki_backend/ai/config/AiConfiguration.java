package com.blocki.blocki_backend.ai.config;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
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

    @Bean
    DocumentGenerationClient documentGenerationClient(
            AiProperties properties,
            IntegrationTokenProvider integrationTokenProvider,
            UserRepository userRepository,
            JsonMapper jsonMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return new DocumentGenerationClient(
                RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build(),
                properties,
                integrationTokenProvider,
                userRepository,
                jsonMapper);
    }
}
