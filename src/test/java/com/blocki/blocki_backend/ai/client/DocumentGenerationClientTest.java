package com.blocki.blocki_backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DocumentGenerationClientTest {

    private final IntegrationTokenProvider integrationTokenProvider = Mockito.mock(IntegrationTokenProvider.class);
    private final UserRepository userRepository = Mockito.mock(UserRepository.class);
    private MockRestServiceServer server;
    private DocumentGenerationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ai.blocki.example");
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties();
        properties.setInternalKey("internal-key");
        client = new DocumentGenerationClient(builder.build(), properties, integrationTokenProvider, userRepository);
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
                        { "ok": true, "status": "proposed", "artifact": { "body_markdown": "# 문서" }, "missing_sources": [] }
                        """, APPLICATION_JSON));

        DocumentGenerationClient.Result result = client.generate(job);

        assertThat(result.markdown()).isEqualTo("# 문서");
        server.verify();
    }
}
