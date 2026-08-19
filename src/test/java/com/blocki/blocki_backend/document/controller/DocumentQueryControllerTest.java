package com.blocki.blocki_backend.document.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.service.DocumentQueryService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentQueryControllerTest {

    private final DocumentQueryService documentQueryService = Mockito.mock(DocumentQueryService.class);
    private final CurrentUserIdResolver currentUserIdResolver = Mockito.mock(CurrentUserIdResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DocumentQueryController(documentQueryService, currentUserIdResolver)).build();
    }

    @Test
    void returns_the_latest_document_content_for_the_current_user() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(documentQueryService.latest(eq(userId), eq(documentId))).thenReturn(
                new DocumentQueryService.DocumentContentResult(
                        documentId,
                        DocumentType.RESUME,
                        "김블로 이력서",
                        2,
                        "# 김블로",
                        Instant.parse("2026-08-19T09:00:00Z"),
                        "AI_GENERATED"));

        mockMvc.perform(get("/api/v1/documents/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(documentId.toString()))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.markdown").value("# 김블로"));
    }
}
