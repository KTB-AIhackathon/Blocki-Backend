package com.blocki.blocki_backend.integration.client.github;

public class GithubOAuthClientException extends RuntimeException {

    public GithubOAuthClientException(String message) {
        super(message);
    }

    public GithubOAuthClientException(Throwable cause) {
        super("GitHub OAuth request failed", cause);
    }
}
