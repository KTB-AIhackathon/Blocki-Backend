package com.blocki.blocki_backend.integration.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import com.blocki.blocki_backend.integration.service.IntegrationResult;
import com.blocki.blocki_backend.integration.service.IntegrationService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationControllerTest {

    private final IntegrationService integrationService = org.mockito.Mockito.mock(IntegrationService.class);
    private final CurrentUserIdResolver currentUserIdResolver = org.mockito.Mockito.mock(CurrentUserIdResolver.class);
    private final UUID userId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new IntegrationController(integrationService, currentUserIdResolver)).build();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
    }

    @Test
    void lists_only_the_current_users_integration_states() throws Exception {
        when(integrationService.listIntegrations(userId)).thenReturn(List.of(
                new IntegrationResult(IntegrationProvider.NOTION, IntegrationStatus.CONNECTED, "Blocki Workspace", Instant.parse("2026-08-19T09:00:00Z"), null),
                new IntegrationResult(IntegrationProvider.GITHUB, IntegrationStatus.NOT_CONNECTED, null, null, null)));

        mockMvc.perform(get("/api/v1/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].provider").value("NOTION"))
                .andExpect(jsonPath("$.data.items[0].accountLabel").value("Blocki Workspace"))
                .andExpect(jsonPath("$.data.items[1].provider").value("GITHUB"));

        verify(integrationService).listIntegrations(userId);
    }

    @Test
    void redirects_to_provider_authorization_for_the_current_user() throws Exception {
        URI authorizeUri = URI.create("https://api.notion.com/v1/oauth/authorize?state=opaque-state");
        when(integrationService.startAuthorization(userId, IntegrationProvider.NOTION)).thenReturn(authorizeUri);

        mockMvc.perform(get("/api/v1/integrations/notion/authorize"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", authorizeUri.toString()));

        verify(integrationService).startAuthorization(userId, IntegrationProvider.NOTION);
    }

    @Test
    void redirects_successful_callback_to_workspace() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/notion/callback")
                        .queryParam("code", "authorization-code")
                        .queryParam("state", "opaque-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/workspace?integration=notion&result=success"));

        verify(integrationService).completeAuthorization(
                IntegrationProvider.NOTION, "authorization-code", "opaque-state");
    }

    @Test
    void redirects_github_callback_to_workspace() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/github/callback")
                        .queryParam("code", "authorization-code")
                        .queryParam("state", "opaque-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/workspace?integration=github&result=success"));

        verify(integrationService).completeAuthorization(
                IntegrationProvider.GITHUB, "authorization-code", "opaque-state");
    }
}
