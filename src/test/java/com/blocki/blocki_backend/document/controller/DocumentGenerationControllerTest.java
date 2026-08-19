package com.blocki.blocki_backend.document.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.service.DocumentGenerationResult;
import com.blocki.blocki_backend.document.service.DocumentGenerationService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentGenerationControllerTest {

    private final DocumentGenerationService documentGenerationService = Mockito.mock(DocumentGenerationService.class);
    private final CurrentUserIdResolver currentUserIdResolver = Mockito.mock(CurrentUserIdResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DocumentGenerationController(documentGenerationService, currentUserIdResolver)).build();
    }

    @Test
    void accepts_a_generation_request_and_returns_a_queued_job() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(documentGenerationService.request(eq(userId), eq(DocumentType.RESUME), eq("request-key")))
                .thenReturn(new DocumentGenerationResult(
                        jobId,
                        "DOCUMENT_GENERATION",
                        "QUEUED",
                        0,
                        1,
                        3,
                        Instant.parse("2026-08-19T09:00:00Z"),
                        null,
                        null,
                        true,
                        null,
                        null,
                        List.of(),
                        null,
                        null));
        when(currentUserIdResolver.resolve()).thenReturn(userId);

        mockMvc.perform(post("/api/v1/documents/generations")
                        .header("Idempotency-Key", "request-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESUME\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/document-generation-jobs/" + jobId))
                .andExpect(jsonPath("$.data.type").value("DOCUMENT_GENERATION"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.missingSources").isEmpty());
    }

    @Test
    void rejects_an_unsupported_document_type_with_the_contract_error_code() throws Exception {
        mockMvc.perform(post("/api/v1/documents/generations")
                        .header("Idempotency-Key", "request-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"LETTER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_DOCUMENT_TYPE"));
    }

    @Test
    void rejects_a_blank_idempotency_key() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(documentGenerationService.request(eq(userId), eq(DocumentType.RESUME), eq("")))
                .thenThrow(new IllegalArgumentException("Invalid document generation request"));

        mockMvc.perform(post("/api/v1/documents/generations")
                        .header("Idempotency-Key", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"RESUME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }
}
