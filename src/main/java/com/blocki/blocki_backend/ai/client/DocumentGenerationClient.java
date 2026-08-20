package com.blocki.blocki_backend.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.blocki.blocki_backend.ai.config.AiProperties;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.user.entity.User;
import com.blocki.blocki_backend.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class DocumentGenerationClient {

    private final RestClient restClient;
    private final AiProperties properties;
    private final IntegrationTokenProvider integrationTokenProvider;
    private final NotionDashboardResolver notionDashboardResolver;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper;

    public DocumentGenerationClient(
            RestClient restClient,
            AiProperties properties,
            IntegrationTokenProvider integrationTokenProvider,
            NotionDashboardResolver notionDashboardResolver,
            UserRepository userRepository,
            JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.integrationTokenProvider = integrationTokenProvider;
        this.notionDashboardResolver = notionDashboardResolver;
        this.userRepository = userRepository;
        this.jsonMapper = jsonMapper;
    }

    public Result generate(DocumentGenerationJob job) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri("/internal/jobs")
                .header("X-Internal-Key", properties.getInternalKey());
        integrationTokenProvider.findAccessToken(job.getUserId(), IntegrationProvider.GITHUB)
                .ifPresent(token -> request.header("X-GitHub-Pat", token));
        InternalNotionTarget notionTarget = notionTarget(job.getUserId(), request);
        User user = userRepository.findById(job.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Document generation user does not exist"));
        String responseBody;
        try {
            responseBody = request.body(new InternalJobRequest(
                            job.getId().toString(),
                            job.getUserId().toString(),
                            "profile_document",
                            new InternalDocument(
                                    job.getDocumentType().name().toLowerCase(),
                                    profileFields(user)),
                            notionTarget))
                    .retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            throw InternalAiClientException.fromResponse(exception);
        } catch (RestClientException exception) {
            throw InternalAiClientException.transport(exception);
        }
        return toResult(responseBody);
    }

    /**
     * Attaches the user's Notion credentials, or leaves them off so the worker
     * skips publishing. Notion is a side effect of generation, never a condition
     * of it: an unconnected or unreachable Notion still yields a document.
     */
    private InternalNotionTarget notionTarget(java.util.UUID userId, RestClient.RequestBodySpec request) {
        String notionToken = notionDashboardResolver.findAccessToken(userId).orElse(null);
        if (notionToken == null) {
            return null;
        }
        request.header("X-Notion-Token", notionToken);
        return notionDashboardResolver.resolveParentPageId(userId, notionToken)
                .map(InternalNotionTarget::new)
                .orElse(null);
    }

    private Result toResult(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw InternalAiClientException.invalidResponse(new IllegalArgumentException("AI response is empty"));
        }
        try {
            InternalJobResponse response = jsonMapper.readValue(responseBody, InternalJobResponse.class);
            return validate(response);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw InternalAiClientException.invalidResponse(exception);
        }
    }

    private static Result validate(InternalJobResponse response) {
        if (response == null || response.ok() == null) {
            throw new IllegalArgumentException("AI response must include ok");
        }
        List<String> missingSources = response.missingSources() == null ? List.of() : response.missingSources();
        if (!missingSources.stream().allMatch("GITHUB"::equals)) {
            throw new IllegalArgumentException("AI response has unsupported missing source");
        }
        if (!response.ok()) {
            if (!"failed".equals(response.status()) || isBlank(response.errorCode())) {
                throw new IllegalArgumentException("AI failure response is invalid");
            }
            return new Result(
                    false,
                    response.status(),
                    null,
                    missingSources,
                    response.errorCode(),
                    response.error() != null && Boolean.TRUE.equals(response.error().retryable()));
        }
        if (!Set.of("proposed", "partial", "no_change").contains(response.status())
                || response.artifact() == null
                || isBlank(response.artifact().kind())
                || isBlank(response.artifact().title())
                || isBlank(response.artifact().bodyMarkdown())) {
            throw new IllegalArgumentException("AI success response is invalid");
        }
        return new Result(true, response.status(), response.artifact().bodyMarkdown(), missingSources, null, false);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ProfileFields profileFields(User user) {
        return new ProfileFields(user.getName(), "", "", "");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record InternalJobRequest(
            @JsonProperty("job_id") String jobId,
            @JsonProperty("user_id") String userId,
            @JsonProperty("job_type") String jobType,
            @JsonProperty("document") InternalDocument document,
            @JsonProperty("notion") InternalNotionTarget notion) {
    }

    private record InternalNotionTarget(@JsonProperty("parent_id") String parentId) {
    }

    private record InternalDocument(
            @JsonProperty("kind") String kind,
            @JsonProperty("profile_fields") ProfileFields profileFields) {
    }

    private record ProfileFields(
            @JsonProperty("name") String name,
            @JsonProperty("contact_md") String contactMd,
            @JsonProperty("experience_md") String experienceMd,
            @JsonProperty("education_md") String educationMd) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InternalJobResponse(
            @JsonProperty("ok") Boolean ok,
            @JsonProperty("status") String status,
            @JsonProperty("artifact") InternalArtifact artifact,
            @JsonProperty("missing_sources") List<String> missingSources,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("error") InternalError error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InternalError(
            @JsonProperty("code") String code,
            @JsonProperty("retryable") Boolean retryable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InternalArtifact(
            @JsonProperty("kind") String kind,
            @JsonProperty("title") String title,
            @JsonProperty("body_markdown") String bodyMarkdown) {
    }

    public record Result(
            boolean ok,
            String status,
            String markdown,
            List<String> missingSources,
            String errorCode,
            boolean retryable) {
        public Result(boolean ok, String status, String markdown, List<String> missingSources, String errorCode) {
            this(ok, status, markdown, missingSources, errorCode, false);
        }
    }
}
