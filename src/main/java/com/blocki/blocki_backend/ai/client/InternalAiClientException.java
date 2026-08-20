package com.blocki.blocki_backend.ai.client;

import org.springframework.web.client.RestClientResponseException;

public class InternalAiClientException extends RuntimeException {

    public enum Category {
        UNAUTHORIZED,
        CLIENT_ERROR,
        SERVER_ERROR,
        TRANSPORT_ERROR,
        INVALID_RESPONSE
    }

    private final Category category;

    public InternalAiClientException(Category category) {
        super(category.name());
        this.category = category;
    }

    private InternalAiClientException(Category category, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
    }

    public static InternalAiClientException fromResponse(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 401) {
            return new InternalAiClientException(Category.UNAUTHORIZED, exception);
        }
        if (exception.getStatusCode().is4xxClientError()) {
            return new InternalAiClientException(Category.CLIENT_ERROR, exception);
        }
        return new InternalAiClientException(Category.SERVER_ERROR, exception);
    }

    public static InternalAiClientException transport(Throwable cause) {
        return new InternalAiClientException(Category.TRANSPORT_ERROR, cause);
    }

    public static InternalAiClientException invalidResponse(Throwable cause) {
        return new InternalAiClientException(Category.INVALID_RESPONSE, cause);
    }

    public Category getCategory() {
        return category;
    }

    public boolean isRetryable() {
        return category == Category.SERVER_ERROR || category == Category.TRANSPORT_ERROR;
    }
}
