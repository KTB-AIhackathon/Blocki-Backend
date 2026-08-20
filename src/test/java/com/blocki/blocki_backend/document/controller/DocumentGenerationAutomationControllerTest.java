package com.blocki.blocki_backend.document.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocki.blocki_backend.common.exception.BusinessException;
import com.blocki.blocki_backend.common.exception.ErrorCode;
import com.blocki.blocki_backend.common.exception.GlobalExceptionHandler;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationResponse;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationUpdateRequest;
import com.blocki.blocki_backend.document.service.DocumentGenerationAutomationService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentGenerationAutomationControllerTest {

    private final DocumentGenerationAutomationService automationService = Mockito.mock(DocumentGenerationAutomationService.class);
    private final CurrentUserIdResolver currentUserIdResolver = Mockito.mock(CurrentUserIdResolver.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DocumentGenerationAutomationController(automationService, currentUserIdResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returns_the_current_users_automation_setting() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(automationService.get(userId)).thenReturn(DocumentGenerationAutomationResponse.of(false));

        mockMvc.perform(get("/api/v1/document-generation-automation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.schedule.dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.schedule.time").value("21:00"))
                .andExpect(jsonPath("$.data.schedule.timezone").value("Asia/Seoul"));
    }

    @Test
    void enables_automation_for_the_current_user() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(automationService.update(eq(userId), any(DocumentGenerationAutomationUpdateRequest.class)))
                .thenReturn(DocumentGenerationAutomationResponse.of(true));

        mockMvc.perform(put("/api/v1/document-generation-automation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void updates_the_weekly_schedule_for_the_current_user() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(automationService.update(eq(userId), any(DocumentGenerationAutomationUpdateRequest.class)))
                .thenReturn(new DocumentGenerationAutomationResponse(
                        true,
                        new DocumentGenerationAutomationResponse.Schedule(
                                "WEDNESDAY", "21:30", "Asia/Seoul")));

        mockMvc.perform(put("/api/v1/document-generation-automation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"schedule\":{\"dayOfWeek\":\"WEDNESDAY\",\"time\":\"21:30\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedule.dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$.data.schedule.time").value("21:30"));

        verify(automationService).update(eq(userId), argThat(request ->
                request.schedule() != null
                        && request.schedule().dayOfWeek().equals("WEDNESDAY")
                        && request.schedule().time().equals("21:30")));
    }

    @Test
    void returns_conflict_when_github_is_required_to_enable_automation() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserIdResolver.resolve()).thenReturn(userId);
        when(automationService.update(eq(userId), any(DocumentGenerationAutomationUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_INTEGRATION_REQUIRED));

        mockMvc.perform(put("/api/v1/document-generation-automation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GITHUB_INTEGRATION_REQUIRED"));
    }

    @Test
    void rejects_a_request_without_enabled() throws Exception {
        mockMvc.perform(put("/api/v1/document-generation-automation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    void rejects_an_invalid_weekly_schedule() throws Exception {
        mockMvc.perform(put("/api/v1/document-generation-automation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"schedule\":{\"dayOfWeek\":\"FUNDAY\",\"time\":\"25:00\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }
}
