package com.blocki.blocki_backend.document.controller;

import com.blocki.blocki_backend.common.response.ApiResponse;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationResponse;
import com.blocki.blocki_backend.document.dto.DocumentGenerationAutomationUpdateRequest;
import com.blocki.blocki_backend.document.service.DocumentGenerationAutomationService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-generation-automation")
@ConditionalOnBean({CurrentUserIdResolver.class, DocumentGenerationAutomationService.class})
public class DocumentGenerationAutomationController {

    private final DocumentGenerationAutomationService automationService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public DocumentGenerationAutomationController(
            DocumentGenerationAutomationService automationService,
            CurrentUserIdResolver currentUserIdResolver) {
        this.automationService = automationService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping
    public ApiResponse<DocumentGenerationAutomationResponse> get() {
        return ApiResponse.of(automationService.get(currentUserIdResolver.resolve()));
    }

    @PutMapping
    public ApiResponse<DocumentGenerationAutomationResponse> update(
            @Valid @RequestBody DocumentGenerationAutomationUpdateRequest request) {
        return ApiResponse.of(automationService.update(
                currentUserIdResolver.resolve(),
                request.enabled(),
                request.schedule().dayOfWeek(),
                request.schedule().time()));
    }
}
