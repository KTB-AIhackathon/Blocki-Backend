package com.blocki.blocki_backend.document.controller;

import com.blocki.blocki_backend.common.response.ApiResponse;
import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.document.dto.DocumentGenerationRequest;
import com.blocki.blocki_backend.document.service.DocumentGenerationException;
import com.blocki.blocki_backend.document.service.DocumentGenerationResult;
import com.blocki.blocki_backend.document.service.DocumentGenerationService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequestMapping("/api/v1/documents")
@ConditionalOnBean({CurrentUserIdResolver.class, DocumentGenerationService.class})
public class DocumentGenerationController {

    private final DocumentGenerationService documentGenerationService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public DocumentGenerationController(
            DocumentGenerationService documentGenerationService,
            CurrentUserIdResolver currentUserIdResolver) {
        this.documentGenerationService = documentGenerationService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @PostMapping("/generations")
    public ResponseEntity<ApiResponse<DocumentGenerationResult>> requestGeneration(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DocumentGenerationRequest request) {
        DocumentGenerationResult result = documentGenerationService.request(
                currentUserIdResolver.resolve(), request.type(), idempotencyKey);
        return ResponseEntity.accepted()
                .header(HttpHeaders.LOCATION, "/api/v1/document-generation-jobs/" + result.id())
                .body(ApiResponse.of(result));
    }

    @ExceptionHandler(DocumentGenerationException.class)
    public ResponseEntity<ErrorResponse> handleGenerationException(DocumentGenerationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case DocumentGenerationException.JOB_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DocumentGenerationException.FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_PARAMETER", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedDocumentType(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "UNSUPPORTED_DOCUMENT_TYPE", "지원하지 않는 문서 유형입니다."));
    }
}
