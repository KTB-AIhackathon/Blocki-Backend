package com.blocki.blocki_backend.integration.controller;

import com.blocki.blocki_backend.common.response.ApiResponse;
import com.blocki.blocki_backend.common.response.ErrorResponse;
import com.blocki.blocki_backend.integration.config.FrontendProperties;
import com.blocki.blocki_backend.integration.entity.IntegrationProvider;
import com.blocki.blocki_backend.integration.service.IntegrationException;
import com.blocki.blocki_backend.integration.service.IntegrationResult;
import com.blocki.blocki_backend.integration.service.IntegrationService;
import com.blocki.blocki_backend.integration.service.NotionConnectHook;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/integrations")
@ConditionalOnBean({CurrentUserIdResolver.class, IntegrationService.class})
public class IntegrationController {

    private static final Logger log = LoggerFactory.getLogger(IntegrationController.class);

    private final IntegrationService integrationService;
    private final CurrentUserIdResolver currentUserIdResolver;
    private final FrontendProperties frontendProperties;
    private final ObjectProvider<NotionConnectHook> notionConnectHook;

    public IntegrationController(
            IntegrationService integrationService,
            CurrentUserIdResolver currentUserIdResolver,
            FrontendProperties frontendProperties,
            ObjectProvider<NotionConnectHook> notionConnectHook) {
        this.integrationService = integrationService;
        this.currentUserIdResolver = currentUserIdResolver;
        this.frontendProperties = frontendProperties;
        this.notionConnectHook = notionConnectHook;
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

    @PostMapping("/{provider}/authorize-url")
    public ApiResponse<AuthorizeUrlResponse> authorizeUrl(@PathVariable String provider) {
        URI authorizeUri = integrationService.startAuthorization(currentUserIdResolver.resolve(), parseProvider(provider));
        return ApiResponse.of(new AuthorizeUrlResponse(authorizeUri.toString()));
    }

    @DeleteMapping("/{provider}")
    public ApiResponse<IntegrationResponse> disconnect(@PathVariable String provider) {
        IntegrationResult result = integrationService.disconnect(currentUserIdResolver.resolve(), parseProvider(provider));
        return ApiResponse.of(IntegrationResponse.from(result));
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
            UUID userId = integrationService.completeAuthorization(integrationProvider, code, state);
            provisionNotionDashboard(integrationProvider, userId);
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

    private void provisionNotionDashboard(IntegrationProvider provider, UUID userId) {
        if (provider != IntegrationProvider.NOTION || userId == null) {
            return;
        }
        NotionConnectHook hook = notionConnectHook.getIfAvailable();
        if (hook == null) {
            return;
        }
        integrationService.findAccessToken(userId, IntegrationProvider.NOTION).ifPresent(token -> {
            try {
                hook.afterNotionConnected(userId, token);
            } catch (RuntimeException exception) {
                log.warn("Notion dashboard was not created during OAuth for user {}", userId, exception);
            }
        });
    }

    private IntegrationProvider parseProvider(String provider) {
        try {
            return IntegrationProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IntegrationException(IntegrationException.UNSUPPORTED_PROVIDER);
        }
    }

    private RedirectView workspaceRedirect(String provider, String result, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendProperties.getOrigin())
                .path("/oauth/callback")
                .queryParam("provider", provider)
                .queryParam("result", result);
        if (errorCode != null) {
            builder.queryParam("error", errorCode);
        }
        RedirectView redirectView = new RedirectView(builder.build().encode().toUriString());
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

    public record AuthorizeUrlResponse(String authorizeUrl) {
    }

    public record IntegrationResponse(
            IntegrationProvider provider,
            String status,
            String accountLabel,
            java.time.Instant connectedAt,
            String errorCode
    ) {
        static IntegrationResponse from(IntegrationResult result) {
            return new IntegrationResponse(
                    result.provider(),
                    result.status().name(),
                    result.accountLabel(),
                    result.connectedAt(),
                    result.errorCode());
        }
    }
}
