package com.blocki.blocki_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocki.blocki_backend.ai.client.DocumentGenerationClient;
import com.blocki.blocki_backend.ai.client.NotionDashboardResolver;
import com.blocki.blocki_backend.document.controller.DocumentGenerationController;
import com.blocki.blocki_backend.document.service.DocumentGenerationWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the application the way it is actually deployed: with the AI worker
 * configured.
 *
 * <p>{@link BlockiBackendApplicationTests} leaves {@code ai.base-url} unset, so
 * every bean behind {@code @ConditionalOnBean(DocumentGenerationClient.class)} is
 * absent there and a wiring fault in the generation path passes CI while making
 * the real application refuse to start.
 */
@SpringBootTest(properties = {
        "ai.base-url=http://ai.blocki.test",
        "ai.internal-key=test-internal-key",
        // Nothing should be claimed while the context is merely being checked.
        "document-generation.poll-delay-ms=3600000",
})
class AiEnabledContextTest {

    @Autowired
    private DocumentGenerationWorker worker;

    @Autowired
    private DocumentGenerationClient client;

    @Autowired
    private NotionDashboardResolver notionDashboardResolver;

    @Autowired
    private DocumentGenerationController generationController;

    @Test
    void the_whole_document_generation_path_is_wired() {
        assertThat(worker).isNotNull();
        assertThat(client).isNotNull();
        assertThat(notionDashboardResolver).isNotNull();
        assertThat(generationController).isNotNull();
    }
}
