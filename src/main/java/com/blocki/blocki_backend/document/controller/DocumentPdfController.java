package com.blocki.blocki_backend.document.controller;

import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.document.service.DocumentPdfService;
import com.blocki.blocki_backend.document.service.DocumentQueryException;
import com.blocki.blocki_backend.document.service.DocumentQueryService;
import com.blocki.blocki_backend.integration.controller.CurrentUserIdResolver;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
@ConditionalOnBean({CurrentUserIdResolver.class, DocumentQueryService.class, DocumentPdfService.class})
public class DocumentPdfController {
    private final DocumentQueryService documentQueryService;
    private final DocumentPdfService documentPdfService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public DocumentPdfController(DocumentQueryService documentQueryService, DocumentPdfService documentPdfService,
                                 CurrentUserIdResolver currentUserIdResolver) {
        this.documentQueryService = documentQueryService;
        this.documentPdfService = documentPdfService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping("/{documentId}/versions/{versionId}/pdf")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId, @PathVariable UUID versionId) {
        var document = documentQueryService.version(currentUserIdResolver.resolve(), documentId, versionId);
        byte[] pdf = documentPdfService.render(document);
        String filename = document.type().name().toLowerCase() + "-v" + document.version() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    @ExceptionHandler(DocumentQueryException.class)
    public ResponseEntity<ErrorResponse> handleQueryException(DocumentQueryException exception) {
        HttpStatus status = exception.getCode().equals(DocumentQueryException.FORBIDDEN)
                ? HttpStatus.FORBIDDEN
                : HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.getCode(), exception.getMessage()));
    }
}
