package com.blocki.blocki_backend.integration.service;

public class IntegrationException extends RuntimeException {

    public static final String OAUTH_STATE_INVALID = "OAUTH_STATE_INVALID";
    public static final String INTEGRATION_ALREADY_CONNECTED = "INTEGRATION_ALREADY_CONNECTED";
    public static final String EXTERNAL_SOURCE_FAILED = "EXTERNAL_SOURCE_FAILED";
    public static final String OAUTH_AUTHORIZATION_DENIED = "OAUTH_AUTHORIZATION_DENIED";
    public static final String UNSUPPORTED_PROVIDER = "UNSUPPORTED_PROVIDER";

    private final String code;

    public IntegrationException(String code) {
        super(code);
        this.code = code;
    }

    public IntegrationException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
