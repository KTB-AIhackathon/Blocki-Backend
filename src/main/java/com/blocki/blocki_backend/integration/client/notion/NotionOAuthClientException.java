package com.blocki.blocki_backend.integration.client.notion;

public class NotionOAuthClientException extends RuntimeException {

    public NotionOAuthClientException(Throwable cause) {
        super("Notion OAuth request failed", cause);
    }
}
