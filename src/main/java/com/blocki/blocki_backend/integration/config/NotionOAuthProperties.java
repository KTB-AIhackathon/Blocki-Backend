package com.blocki.blocki_backend.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notion")
public class NotionOAuthProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;

    /**
     * Notion's own endpoints, overridable so a local stack can stand a fake
     * identity provider in front of the real browser flow. Nothing but tests and
     * local development should ever set these.
     */
    private String authorizeUri = "https://api.notion.com/v1/oauth/authorize";
    private String tokenUri = "https://api.notion.com/v1/oauth/token";

    public String getAuthorizeUri() {
        return authorizeUri;
    }

    public void setAuthorizeUri(String authorizeUri) {
        this.authorizeUri = authorizeUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}
