package com.blocki.blocki_backend.document.controller;

import com.blocki.blocki_backend.common.response.ApiResponse;
import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.document.service.DocumentGenerationException;
import com.blocki.blocki_backend.document.service.DocumentGenerationResult;
import com.blocki.blocki_backend.document.service.DocumentGenerationService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-generation-jobs")
@ConditionalOnBean({CurrentUserIdResolver.class, DocumentGenerationService.class})
public class DocumentGenerationJobController {

    private final DocumentGenerationService documentGenerationService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public DocumentGenerationJobController(
            DocumentGenerationService documentGenerationService,
            CurrentUserIdResolver currentUserIdResolver) {
        this.documentGenerationService = documentGenerationService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping("/{jobId}")
    public ApiResponse<DocumentGenerationResult> get(@PathVariable UUID jobId) {
        return ApiResponse.of(documentGenerationService.get(currentUserIdResolver.resolve(), jobId));
    }

    @ExceptionHandler(DocumentGenerationException.class)
    public ResponseEntity<ErrorResponse> handle(DocumentGenerationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case DocumentGenerationException.JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DocumentGenerationException.FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
