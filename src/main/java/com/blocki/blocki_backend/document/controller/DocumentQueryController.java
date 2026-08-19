package com.blocki.blocki_backend.document.controller;

import com.blocki.blocki_backend.common.response.ApiResponse;
import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.document.entity.DocumentType;
import com.blocki.blocki_backend.document.service.DocumentQueryException;
import com.blocki.blocki_backend.document.service.DocumentQueryService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@ConditionalOnBean({CurrentUserIdResolver.class, DocumentQueryService.class})
public class DocumentQueryController {

    private final DocumentQueryService documentQueryService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public DocumentQueryController(DocumentQueryService documentQueryService, CurrentUserIdResolver currentUserIdResolver) {
        this.documentQueryService = documentQueryService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping
    public ApiResponse<DocumentListResponse> list(
            @RequestParam(required = false) DocumentType type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "latestVersionCreatedAt,DESC") String sort) {
        boolean ascending = direction(sort, "latestVersionCreatedAt");
        var result = documentQueryService.list(currentUserIdResolver.resolve(), type, page, size, ascending);
        return ApiResponse.of(new DocumentListResponse(
                result.items(), PageResponse.of(result.page(), result.size(), result.totalElements(), sort)));
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentQueryService.DocumentContentResult> latest(@PathVariable UUID documentId) {
        return ApiResponse.of(documentQueryService.latest(currentUserIdResolver.resolve(), documentId));
    }

    @GetMapping("/{documentId}/versions")
    public ApiResponse<DocumentVersionListResponse> versions(
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "version,DESC") String sort) {
        boolean ascending = direction(sort, "version");
        var result = documentQueryService.versions(currentUserIdResolver.resolve(), documentId, page, size, ascending);
        return ApiResponse.of(new DocumentVersionListResponse(
                result.items(), PageResponse.of(result.page(), result.size(), result.totalElements(), sort)));
    }

    @GetMapping("/{documentId}/versions/{versionId}")
    public ApiResponse<DocumentQueryService.DocumentContentResult> version(
            @PathVariable UUID documentId, @PathVariable UUID versionId) {
        return ApiResponse.of(documentQueryService.version(currentUserIdResolver.resolve(), documentId, versionId));
    }

    @ExceptionHandler(DocumentQueryException.class)
    public ResponseEntity<ErrorResponse> handleQueryException(DocumentQueryException exception) {
        HttpStatus status = exception.getCode().equals(DocumentQueryException.FORBIDDEN)
                ? HttpStatus.FORBIDDEN
                : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_PARAMETER", exception.getMessage()));
    }

    private boolean direction(String sort, String field) {
        if ((field + ",ASC").equals(sort)) {
            return true;
        }
        if ((field + ",DESC").equals(sort)) {
            return false;
        }
        throw new IllegalArgumentException("지원하지 않는 정렬 조건입니다.");
    }

    public record DocumentListResponse(
            java.util.List<DocumentQueryService.DocumentSummary> items,
            PageResponse page) {
    }

    public record DocumentVersionListResponse(
            java.util.List<DocumentQueryService.DocumentVersionSummary> items,
            PageResponse page) {
    }

    public record PageResponse(int page, int size, long totalElements, int totalPages, String sort) {
        private static PageResponse of(int page, int size, long totalElements, String sort) {
            return new PageResponse(page, size, totalElements, (int) Math.ceil((double) totalElements / size), sort);
        }
    }
}
