package com.blocki.blocki_backend.ai.config;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.user.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = {"ai.base-url", "ai.internal-key"})
public class AiConfiguration {

    @Bean
    DocumentGenerationClient documentGenerationClient(
            AiProperties properties,
            IntegrationTokenProvider integrationTokenProvider,
            UserRepository userRepository) {
        return new DocumentGenerationClient(
                RestClient.builder().baseUrl(properties.getBaseUrl()).build(),
                properties,
                integrationTokenProvider,
                userRepository);
    }
}
