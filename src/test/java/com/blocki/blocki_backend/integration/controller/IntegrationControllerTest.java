package com.blocki.blocki_backend.integration.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.entity.IntegrationStatus;
import com.blocki.blocki_backend.integration.config.FrontendProperties;
import com.blocki.blocki_backend.integration.service.IntegrationResult;
import com.blocki.blocki_backend.integration.service.IntegrationService;
import com.blocki.blocki_backend.integration.service.NotionConnectHook;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationControllerTest {

    private final IntegrationService integrationService = org.mockito.Mockito.mock(IntegrationService.class);
    private final CurrentUserIdResolver currentUserIdResolver = org.mockito.Mockito.mock(CurrentUserIdResolver.class);
    private final UUID userId = UUID.randomUUID();
    private final FrontendProperties frontendProperties = new FrontendProperties();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<NotionConnectHook> hooks = org.mockito.Mockito.mock(ObjectProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new IntegrationController(
                integrationService, currentUserIdResolver, frontendProperties, hooks)).build();
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
    void returns_an_authorize_url_for_the_authenticated_user() throws Exception {
        URI authorizeUri = URI.create("https://github.com/login/oauth/authorize?client_id=client-id&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcallback&state=opaque-state");
        when(integrationService.startAuthorization(userId, IntegrationProvider.GITHUB)).thenReturn(authorizeUri);

        mockMvc.perform(post("/api/v1/integrations/github/authorize-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizeUrl").value(authorizeUri.toString()));

        verify(integrationService).startAuthorization(userId, IntegrationProvider.GITHUB);
    }

    @Test
    void redirects_successful_callback_to_the_frontend_callback_page() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/notion/callback")
                        .queryParam("code", "authorization-code")
                        .queryParam("state", "opaque-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/oauth/callback?provider=notion&result=success"));

        verify(integrationService).completeAuthorization(
                IntegrationProvider.NOTION, "authorization-code", "opaque-state");
    }

    @Test
    void creates_the_notion_dashboard_after_a_successful_callback() throws Exception {
        NotionConnectHook hook = org.mockito.Mockito.mock(NotionConnectHook.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotionConnectHook> hooks = org.mockito.Mockito.mock(ObjectProvider.class);
        when(hooks.getIfAvailable()).thenReturn(hook);
        when(integrationService.completeAuthorization(
                IntegrationProvider.NOTION, "authorization-code", "opaque-state")).thenReturn(userId);
        when(integrationService.findAccessToken(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of("notion-token"));
        when(integrationService.findNotionDashboardPageId(userId)).thenReturn(Optional.of("dash-1"));
        mockMvc = MockMvcBuilders.standaloneSetup(new IntegrationController(
                integrationService, currentUserIdResolver, frontendProperties, hooks)).build();

        mockMvc.perform(get("/api/v1/integrations/notion/callback")
                        .queryParam("code", "authorization-code")
                        .queryParam("state", "opaque-state"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5173/oauth/callback?provider=notion&result=success"));

        verify(hook).afterNotionConnected(userId, "notion-token");
    }

    @Test
    void warns_when_notion_connects_but_the_dashboard_was_not_created() throws Exception {
        NotionConnectHook hook = org.mockito.Mockito.mock(NotionConnectHook.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotionConnectHook> hooks = org.mockito.Mockito.mock(ObjectProvider.class);
        when(hooks.getIfAvailable()).thenReturn(hook);
        when(integrationService.completeAuthorization(
                IntegrationProvider.NOTION, "authorization-code", "opaque-state")).thenReturn(userId);
        when(integrationService.findAccessToken(userId, IntegrationProvider.NOTION))
                .thenReturn(Optional.of("notion-token"));
        when(integrationService.findNotionDashboardPageId(userId)).thenReturn(Optional.empty());
        mockMvc = MockMvcBuilders.standaloneSetup(new IntegrationController(
                integrationService, currentUserIdResolver, frontendProperties, hooks)).build();

        mockMvc.perform(get("/api/v1/integrations/notion/callback")
                        .queryParam("code", "authorization-code")
                        .queryParam("state", "opaque-state"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5173/oauth/callback?provider=notion&result=success&error=NOTION_PAGE_ACCESS"));
    }

    @Test
    void redirects_a_provider_denial_to_the_frontend_callback_page() throws Exception {
        mockMvc.perform(get("/api/v1/integrations/github/callback")
                        .queryParam("error", "access_denied")
                        .queryParam("state", "opaque-state"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5173/oauth/callback?provider=github&result=failed&error=OAUTH_AUTHORIZATION_DENIED"));

        verify(integrationService).cancelAuthorization(IntegrationProvider.GITHUB, "opaque-state");
    }

    @Test
    void disconnects_the_authenticated_users_provider() throws Exception {
        when(integrationService.disconnect(userId, IntegrationProvider.GITHUB)).thenReturn(
                new IntegrationResult(IntegrationProvider.GITHUB, IntegrationStatus.NOT_CONNECTED, null, null, null));

        mockMvc.perform(delete("/api/v1/integrations/github"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("GITHUB"))
                .andExpect(jsonPath("$.data.status").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.data.accountLabel").doesNotExist());

        verify(integrationService).disconnect(userId, IntegrationProvider.GITHUB);
    }
}
