package com.blocki.blocki_backend.document.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.document.service.DocumentGenerationException;
import com.blocki.blocki_backend.document.service.DocumentGenerationService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentGenerationJobControllerTest {

    private final DocumentGenerationService service = Mockito.mock(DocumentGenerationService.class);
    private final CurrentUserIdResolver currentUserIdResolver = Mockito.mock(CurrentUserIdResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DocumentGenerationJobController(service, currentUserIdResolver)).build();
    }

    @Test
    void returns_forbidden_for_another_users_job() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(service.get(eq(userId), eq(jobId))).thenThrow(DocumentGenerationException.forbidden());

        mockMvc.perform(get("/api/v1/document-generation-jobs/{jobId}", jobId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void returns_not_found_for_a_missing_job() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(service.get(eq(userId), eq(jobId))).thenThrow(DocumentGenerationException.jobNotFound());

        mockMvc.perform(get("/api/v1/document-generation-jobs/{jobId}", jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }
}
