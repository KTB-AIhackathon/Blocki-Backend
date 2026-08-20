package com.blocki.blocki_backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.blocki.blocki_backend.ai.config.AiProperties;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.user.entity.User;
import com.blocki.blocki_backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class DocumentGenerationClientTest {

    private static final String PROPOSED = """
            { "ok": true, "status": "proposed", "artifact": { "kind": "resume", "title": "이력서", "body_markdown": "# 문서" }, "missing_sources": [] }
            """;

    private final IntegrationTokenProvider integrationTokenProvider = Mockito.mock(IntegrationTokenProvider.class);
    private final NotionDashboardResolver notionDashboardResolver = Mockito.mock(NotionDashboardResolver.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private MockRestServiceServer server;
    private DocumentGenerationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.blocki.example");
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties();
        properties.setInternalKey("internal-key");
        when(notionDashboardResolver.findAccessToken(Mockito.any())).thenReturn(Optional.empty());
        client = new DocumentGenerationClient(
                builder.build(),
                properties,
                integrationTokenProvider,
                notionDashboardResolver,
                userRepository,
                new JsonMapper());
    }

    @Test
    void sends_profile_fields_and_the_connected_github_token_to_the_internal_api() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        DocumentGenerationJob job = Mockito.mock(DocumentGenerationJob.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getUserId()).thenReturn(userId);
        when(job.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(integrationTokenProvider.findAccessToken(userId, IntegrationProvider.GITHUB))
                .thenReturn(Optional.of("github-token"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.register("hash", "김블로", "blocki@example.com")));
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", "internal-key"))
                .andExpect(header("X-GitHub-Pat", "github-token"))
                .andExpect(header(CONTENT_TYPE, APPLICATION_JSON.toString()))
                .andExpect(content().json("""
                        {
                          "job_id": "%s",
                          "user_id": "%s",
                          "job_type": "profile_document",
                          "document": {
                            "kind": "resume",
                            "profile_fields": {
                              "name": "김블로",
                              "contact_md": "",
                              "experience_md": "",
                              "education_md": ""
                            }
                          }
                        }
                        """.formatted(jobId, userId)))
                .andRespond(withSuccess("""
                        { "ok": true, "status": "proposed", "artifact": { "kind": "resume", "title": "이력서", "body_markdown": "# 문서" }, "missing_sources": [] }
                        """, APPLICATION_JSON));

        DocumentGenerationClient.Result result = client.generate(job);

        assertThat(result.markdown()).isEqualTo("# 문서");
        server.verify();
    }

    @Test
    void a_connected_notion_account_sends_its_token_and_the_dashboard_to_write_under() {
        DocumentGenerationJob job = job();
        when(notionDashboardResolver.findAccessToken(job.getUserId())).thenReturn(Optional.of("notion-token"));
        when(notionDashboardResolver.resolveParentPageId(job.getUserId(), "notion-token"))
                .thenReturn(Optional.of("dashboard-page"));
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andExpect(header("X-Notion-Token", "notion-token"))
                .andExpect(content().json("""
                        { "notion": { "parent_id": "dashboard-page" } }
                        """))
                .andRespond(withSuccess(PROPOSED, APPLICATION_JSON));

        assertThat(client.generate(job).markdown()).isEqualTo("# 문서");
        server.verify();
    }

    @Test
    void an_account_without_notion_asks_for_no_publishing_at_all() {
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andExpect(headerDoesNotExist("X-Notion-Token"))
                .andExpect(jsonPath("$.notion").doesNotExist())
                .andRespond(withSuccess(PROPOSED, APPLICATION_JSON));

        assertThat(client.generate(job()).markdown()).isEqualTo("# 문서");
        server.verify();
    }

    @Test
    void an_unreachable_dashboard_still_generates_the_document() {
        DocumentGenerationJob job = job();
        when(notionDashboardResolver.findAccessToken(job.getUserId())).thenReturn(Optional.of("notion-token"));
        when(notionDashboardResolver.resolveParentPageId(job.getUserId(), "notion-token"))
                .thenReturn(Optional.empty());
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andExpect(header("X-Notion-Token", "notion-token"))
                .andExpect(jsonPath("$.notion").doesNotExist())
                .andRespond(withSuccess(PROPOSED, APPLICATION_JSON));

        assertThat(client.generate(job).markdown()).isEqualTo("# 문서");
        server.verify();
    }

    @Test
    void rejects_an_unexpected_success_status() {
        DocumentGenerationJob job = job();
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(withSuccess("""
                        { "ok": true, "status": "pending", "artifact": { "kind": "resume", "title": "이력서", "body_markdown": "# 문서" } }
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(job))
                .isInstanceOf(InternalAiClientException.class)
                .extracting(exception -> ((InternalAiClientException) exception).getCategory())
                .isEqualTo(InternalAiClientException.Category.INVALID_RESPONSE);
    }

    @Test
    void rejects_a_success_response_without_markdown_artifact() {
        DocumentGenerationJob job = job();
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(withSuccess("""
                        { "ok": true, "status": "proposed", "artifact": {} }
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(job))
                .isInstanceOf(InternalAiClientException.class)
                .extracting(exception -> ((InternalAiClientException) exception).getCategory())
                .isEqualTo(InternalAiClientException.Category.INVALID_RESPONSE);
    }

    @Test
    void rejects_malformed_json_response_as_invalid_response() {
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(withSuccess("{", APPLICATION_JSON));

        assertThatThrownBy(() -> client.generate(job()))
                .isInstanceOf(InternalAiClientException.class)
                .extracting(exception -> ((InternalAiClientException) exception).getCategory())
                .isEqualTo(InternalAiClientException.Category.INVALID_RESPONSE);
    }

    @Test
    void returns_partial_result_with_missing_github_source() {
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(withSuccess("""
                        { "ok": true, "status": "partial", "artifact": { "kind": "resume", "title": "이력서", "body_markdown": "# 문서" }, "missing_sources": ["GITHUB"] }
                        """, APPLICATION_JSON));

        DocumentGenerationClient.Result result = client.generate(job());

        assertThat(result.status()).isEqualTo("partial");
        assertThat(result.missingSources()).containsExactly("GITHUB");
    }

    @Test
    void classifies_unauthorized_response_as_permanent_failure() {
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.generate(job()))
                .isInstanceOf(InternalAiClientException.class)
                .extracting(exception -> ((InternalAiClientException) exception).getCategory())
                .isEqualTo(InternalAiClientException.Category.UNAUTHORIZED);
    }

    @Test
    void classifies_other_4xx_responses_as_permanent_failure() {
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.generate(job()))
                .isInstanceOf(InternalAiClientException.class)
                .extracting(exception -> ((InternalAiClientException) exception).getCategory())
                .isEqualTo(InternalAiClientException.Category.CLIENT_ERROR);
    }

    @Test
    void classifies_5xx_response_as_retryable_failure() {
        server.expect(requestTo("https://ai.blocki.example/internal/jobs"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.generate(job()))
                .isInstanceOf(InternalAiClientException.class)
                .extracting(exception -> ((InternalAiClientException) exception).getCategory())
                .isEqualTo(InternalAiClientException.Category.SERVER_ERROR);
    }

    private DocumentGenerationJob job() {
        UUID userId = UUID.randomUUID();
        DocumentGenerationJob job = Mockito.mock(DocumentGenerationJob.class);
        when(job.getId()).thenReturn(UUID.randomUUID());
        when(job.getUserId()).thenReturn(userId);
        when(job.getDocumentType()).thenReturn(DocumentType.RESUME);
        when(integrationTokenProvider.findAccessToken(userId, IntegrationProvider.GITHUB)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.register("hash", "김블로", "blocki@example.com")));
        return job;
    }
}
