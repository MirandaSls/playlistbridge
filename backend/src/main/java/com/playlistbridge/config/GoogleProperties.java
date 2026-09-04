package com.playlistbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google")
public class GoogleProperties {
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "http://localhost:8080/login/oauth2/code/google";
    private String scope = "https://www.googleapis.com/auth/youtube";

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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
