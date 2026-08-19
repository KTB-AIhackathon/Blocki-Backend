package com.blocki.blocki_backend.document.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.service.DocumentPdfService;
import com.blocki.blocki_backend.document.service.DocumentQueryException;
import com.blocki.blocki_backend.document.service.DocumentQueryService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentPdfControllerTest {

    private final DocumentQueryService queryService = Mockito.mock(DocumentQueryService.class);
    private final DocumentPdfService pdfService = Mockito.mock(DocumentPdfService.class);
    private final CurrentUserIdResolver currentUserIdResolver = Mockito.mock(CurrentUserIdResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DocumentPdfController(queryService, pdfService, currentUserIdResolver)).build();
    }

    @Test
    void returns_pdf_with_the_versioned_attachment_name() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        var result = new DocumentQueryService.DocumentContentResult(
                documentId, DocumentType.RESUME, "이력서", 2, "# 내용", Instant.now(), "AI_GENERATED");
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(queryService.version(eq(userId), eq(documentId), eq(versionId))).thenReturn(result);
        when(pdfService.render(result)).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/v1/documents/{documentId}/versions/{versionId}/pdf", documentId, versionId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"resume-v2.pdf\""));
    }

    @Test
    void returns_not_found_when_the_document_does_not_exist() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(queryService.version(eq(userId), eq(documentId), eq(versionId)))
                .thenThrow(DocumentQueryException.documentNotFound());

        mockMvc.perform(get("/api/v1/documents/{documentId}/versions/{versionId}/pdf", documentId, versionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
    }
}
