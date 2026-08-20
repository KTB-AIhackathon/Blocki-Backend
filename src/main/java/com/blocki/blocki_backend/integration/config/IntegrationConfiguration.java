package com.blocki.blocki_backend.integration.config;

import com.blocki.blocki_backend.document.service.DocumentGenerationAutomationService;
import com.blocki.blocki_backend.integration.client.github.GithubOAuthClient;
import com.blocki.blocki_backend.integration.client.notion.NotionOAuthClient;
import com.blocki.blocki_backend.integration.repository.IntegrationRepository;
import com.blocki.blocki_backend.integration.repository.OAuthStateRepository;
import com.blocki.blocki_backend.integration.security.OAuthStateGenerator;
import com.blocki.blocki_backend.integration.security.TokenEncryptor;
import com.blocki.blocki_backend.integration.service.IntegrationService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnBean(TokenEncryptor.class)
public class IntegrationConfiguration {

    @Bean
    OAuthStateGenerator oauthStateGenerator() {
        return new OAuthStateGenerator();
    }

    @Bean
    NotionOAuthClient notionOAuthClient(NotionOAuthProperties properties) {
        return new NotionOAuthClient(RestClient.create(), properties);
    }

    @Bean
    GithubOAuthClient githubOAuthClient(GithubOAuthProperties properties) {
        return new GithubOAuthClient(RestClient.create(), properties);
    }

    @Bean
    IntegrationService integrationService(
            IntegrationRepository integrationRepository,
            OAuthStateRepository oauthStateRepository,
            NotionOAuthClient notionOAuthClient,
            GithubOAuthClient githubOAuthClient,
            OAuthStateGenerator stateGenerator,
            TokenEncryptor tokenEncryptor,
            DocumentGenerationAutomationService documentGenerationAutomationService) {
        return new IntegrationService(
                integrationRepository,
                oauthStateRepository,
                notionOAuthClient,
                githubOAuthClient,
                stateGenerator,
                tokenEncryptor,
                documentGenerationAutomationService,
                Clock.systemUTC());
    }
}
