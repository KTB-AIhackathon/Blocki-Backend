package com.blocki.blocki_backend.integration.controller;

import com.blocki.blocki_backend.common.response.ApiResponse;
import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.service.IntegrationException;
import com.blocki.blocki_backend.integration.service.IntegrationResult;
import com.blocki.blocki_backend.integration.service.IntegrationService;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/integrations")
@ConditionalOnBean({CurrentUserIdResolver.class, IntegrationService.class})
public class IntegrationController {

    private final IntegrationService integrationService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public IntegrationController(
            IntegrationService integrationService, CurrentUserIdResolver currentUserIdResolver) {
        this.integrationService = integrationService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping
    public ApiResponse<IntegrationListResponse> list() {
        List<IntegrationResponse> items = integrationService.listIntegrations(currentUserIdResolver.resolve())
                .stream()
                .map(IntegrationResponse::from)
                .toList();
        return ApiResponse.of(new IntegrationListResponse(items));
    }

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider) {
        URI authorizeUri = integrationService.startAuthorization(currentUserIdResolver.resolve(), parseProvider(provider));
        return ResponseEntity.status(HttpStatus.FOUND).location(authorizeUri).build();
    }

    @GetMapping("/{provider}/callback")
    public RedirectView callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        IntegrationProvider integrationProvider = parseProvider(provider);
        try {
            if (error != null && !error.isBlank()) {
                integrationService.cancelAuthorization(integrationProvider, state);
                return workspaceRedirect(provider, "failed", IntegrationException.OAUTH_AUTHORIZATION_DENIED);
            }
            integrationService.completeAuthorization(integrationProvider, code, state);
            return workspaceRedirect(provider, "success", null);
        } catch (IntegrationException exception) {
            return workspaceRedirect(provider, "failed", exception.getCode());
        }
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrationException(IntegrationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case IntegrationException.INTEGRATION_ALREADY_CONNECTED -> HttpStatus.CONFLICT;
            case IntegrationException.EXTERNAL_SOURCE_FAILED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(exception.getCode(), messageFor(exception.getCode())));
    }

    private IntegrationProvider parseProvider(String provider) {
        try {
            return IntegrationProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IntegrationException(IntegrationException.UNSUPPORTED_PROVIDER);
        }
    }

    private RedirectView workspaceRedirect(String provider, String result, String errorCode) {
        String url = "/workspace?integration=" + provider + "&result=" + result;
        if (errorCode != null) {
            url += "&error=" + errorCode;
        }
        RedirectView redirectView = new RedirectView(url);
        redirectView.setStatusCode(HttpStatus.FOUND);
        return redirectView;
    }

    private String messageFor(String code) {
        return switch (code) {
            case IntegrationException.UNSUPPORTED_PROVIDER -> "지원하지 않는 연동 제공자입니다.";
            case IntegrationException.INTEGRATION_ALREADY_CONNECTED -> "이미 연결된 연동 제공자입니다.";
            case IntegrationException.EXTERNAL_SOURCE_FAILED -> "외부 제공자 호출에 실패했습니다.";
            default -> "연동 요청을 확인할 수 없습니다. 다시 시도해주세요.";
        };
    }

    public record IntegrationListResponse(List<IntegrationResponse> items) {
    }

    public record IntegrationResponse(
            IntegrationProvider provider,
            String status,
            String accountLabel,
            java.time.Instant connectedAt,
            String errorCode
    ) {
        private static IntegrationResponse from(IntegrationResult result) {
            return new IntegrationResponse(
                    result.provider(),
                    result.status().name(),
                    result.accountLabel(),
                    result.connectedAt(),
                    result.errorCode());
        }
    }
}
