package com.blocki.blocki_backend.ai.client;

import com.blocki.blocki_backend.ai.config.AiProperties;
import com.blocki.blocki_backend.document.entity.DocumentGenerationJob;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.service.IntegrationTokenProvider;
import com.blocki.blocki_backend.user.entity.User;
import com.blocki.blocki_backend.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

public class DocumentGenerationClient {

    private final RestClient restClient;
    private final AiProperties properties;
    private final IntegrationTokenProvider integrationTokenProvider;
    private final UserRepository userRepository;

    public DocumentGenerationClient(
            RestClient restClient,
            AiProperties properties,
            IntegrationTokenProvider integrationTokenProvider,
            UserRepository userRepository) {
        this.restClient = restClient;
        this.properties = properties;
        this.integrationTokenProvider = integrationTokenProvider;
        this.userRepository = userRepository;
    }

    @SuppressWarnings("unchecked")
    public Result generate(DocumentGenerationJob job) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri("/internal/jobs")
                .header("X-Internal-Key", properties.getInternalKey());
        integrationTokenProvider.findAccessToken(job.getUserId(), IntegrationProvider.GITHUB)
                .ifPresent(token -> request.header("X-GitHub-Pat", token));
        User user = userRepository.findById(job.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Document generation user does not exist"));
        Map<String, Object> response = request.body(Map.of(
                        "job_id", job.getId().toString(),
                        "user_id", job.getUserId().toString(),
                        "job_type", "profile_document",
                        "document", Map.of(
                                "kind", job.getDocumentType().name().toLowerCase(),
                                "profile_fields", profileFields(user))))
                .retrieve().body(Map.class);
        if (response == null) {
            throw new IllegalArgumentException("AI response is empty");
        }
        Map<String, Object> artifact = response.get("artifact") instanceof Map<?, ?> value
                ? (Map<String, Object>) value
                : Map.of();
        String markdown = string(artifact.get("body_markdown"));
        return new Result(
                Boolean.TRUE.equals(response.get("ok")),
                string(response.get("status")),
                markdown,
                strings(response.get("missing_sources")),
                string(response.get("error_code")));
    }

    private static String string(Object value) {
        return value instanceof String string ? string : null;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static Map<String, String> profileFields(User user) {
        return Map.of(
                "name", user.getName(),
                "contact_md", "",
                "experience_md", "",
                "education_md", "");
    }

    public record Result(boolean ok, String status, String markdown, List<String> missingSources, String errorCode) {
    }
}
