package com.blocki.blocki_backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.blocki.blocki_backend.ai.config.AiProperties;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.integration.service.NotionDashboardStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotionDashboardResolverTest {

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ENSURE = "https://ai.blocki.example/internal/notion/dashboard";

    private final NotionDashboardStore dashboardStore = Mockito.mock(NotionDashboardStore.class);
    private MockRestServiceServer server;
    private NotionDashboardResolver resolver;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.blocki.example");
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties();
        properties.setInternalKey("internal-key");
        resolver = new NotionDashboardResolver(
                builder.build(),
                properties,
                Mockito.mock(IntegrationTokenProvider.class),
                dashboardStore);
    }

    @Test
    void the_first_run_creates_the_dashboard_and_remembers_where_it_landed() {
        when(dashboardStore.findNotionDashboardPageId(USER)).thenReturn(Optional.empty());
        server.expect(requestTo(ENSURE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", "internal-key"))
                .andExpect(header("X-Notion-Token", "notion-token"))
                .andExpect(content().json("""
                        { "user_id": "11111111-1111-1111-1111-111111111111" }
                        """))
                .andRespond(withSuccess("""
                        { "ok": true, "page_id": "page-1", "page_url": "https://notion.so/page-1", "created": true }
                        """, APPLICATION_JSON));

        assertThat(resolver.resolveParentPageId(USER, "notion-token")).contains("page-1");
        verify(dashboardStore).rememberNotionDashboardPageId(USER, "page-1");
        server.verify();
    }

    @Test
    void a_returning_user_sends_the_page_we_already_know_and_is_not_written_again() {
        when(dashboardStore.findNotionDashboardPageId(USER)).thenReturn(Optional.of("page-1"));
        server.expect(requestTo(ENSURE))
                .andExpect(content().json("""
                        { "known_page_id": "page-1" }
                        """))
                .andRespond(withSuccess("""
                        { "ok": true, "page_id": "page-1", "created": false }
                        """, APPLICATION_JSON));

        assertThat(resolver.resolveParentPageId(USER, "notion-token")).contains("page-1");
        verify(dashboardStore, never()).rememberNotionDashboardPageId(Mockito.any(), Mockito.any());
    }

    @Test
    void a_page_the_user_deleted_is_replaced_by_the_one_the_worker_rebuilt() {
        when(dashboardStore.findNotionDashboardPageId(USER)).thenReturn(Optional.of("page-gone"));
        server.expect(requestTo(ENSURE)).andRespond(withSuccess("""
                { "ok": true, "page_id": "page-new", "created": true }
                """, APPLICATION_JSON));

        assertThat(resolver.resolveParentPageId(USER, "notion-token")).contains("page-new");
        verify(dashboardStore).rememberNotionDashboardPageId(USER, "page-new");
    }

    @Test
    void a_worker_that_is_down_falls_back_to_the_page_we_stored() {
        when(dashboardStore.findNotionDashboardPageId(USER)).thenReturn(Optional.of("page-1"));
        server.expect(requestTo(ENSURE)).andRespond(withServerError());

        assertThat(resolver.resolveParentPageId(USER, "notion-token")).contains("page-1");
    }

    @Test
    void a_worker_that_is_down_on_the_very_first_run_publishes_nowhere() {
        when(dashboardStore.findNotionDashboardPageId(USER)).thenReturn(Optional.empty());
        server.expect(requestTo(ENSURE)).andRespond(withServerError());

        assertThat(resolver.resolveParentPageId(USER, "notion-token")).isEmpty();
        verify(dashboardStore, never()).rememberNotionDashboardPageId(Mockito.any(), Mockito.any());
    }

    @Test
    void a_refused_ensure_is_not_mistaken_for_a_page() {
        when(dashboardStore.findNotionDashboardPageId(USER)).thenReturn(Optional.empty());
        server.expect(requestTo(ENSURE)).andRespond(withSuccess("""
                { "ok": false, "error": { "code": "validation", "message": "Notion token header is required" } }
                """, APPLICATION_JSON));

        assertThat(resolver.resolveParentPageId(USER, "notion-token")).isEmpty();
        verify(dashboardStore, never()).rememberNotionDashboardPageId(Mockito.any(), Mockito.any());
    }
}
