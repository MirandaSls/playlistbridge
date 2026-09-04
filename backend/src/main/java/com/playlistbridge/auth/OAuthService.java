package com.playlistbridge.auth;

import com.playlistbridge.config.GoogleProperties;
import com.playlistbridge.config.SpotifyProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Service
public class OAuthService {
    private static final String SPOTIFY_AUTHORIZATION_ENDPOINT = "https://accounts.spotify.com/authorize";
    private static final String GOOGLE_AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String SPOTIFY_SCOPE = "playlist-read-private playlist-read-collaborative";

    private final SpotifyProperties spotifyProperties;
    private final GoogleProperties googleProperties;
    private final OAuthSessionStore sessionStore;
    private final OAuthTokenClient tokenClient;
    private final String spotifyRedirectUri;
    private final String frontendPublicUrl;

    public OAuthService(
            SpotifyProperties spotifyProperties,
            GoogleProperties googleProperties,
            OAuthSessionStore sessionStore,
            OAuthTokenClient tokenClient,
            @Value("${spotify.redirect-uri:http://localhost:8080/login/oauth2/code/spotify}") String spotifyRedirectUri,
            @Value("${frontend.public-url:http://localhost:3000}") String frontendPublicUrl) {
        this.spotifyProperties = spotifyProperties;
        this.googleProperties = googleProperties;
        this.sessionStore = sessionStore;
        this.tokenClient = tokenClient;
        this.spotifyRedirectUri = spotifyRedirectUri;
        this.frontendPublicUrl = ensureTrailingSlash(frontendPublicUrl);
    }

    public AuthorizationStart begin(OAuthProvider provider, String existingSessionId) {
        if (!isConfigured(provider)) {
            throw new OAuthConfigurationException(provider.routeName() + " OAuth is not configured");
        }
        OAuthSessionStore.Authorization authorization = sessionStore.begin(existingSessionId, provider);
        String redirectUri = redirectUri(provider);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authorizationEndpoint(provider))
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId(provider))
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope(provider))
                .queryParam("state", authorization.state());
        if (provider == OAuthProvider.GOOGLE) {
            builder.queryParam("access_type", "offline").queryParam("prompt", "consent");
        }
        return new AuthorizationStart(authorization.sessionId(), builder.encode().build().toUri());
    }

    public URI complete(
            OAuthProvider provider,
            String sessionId,
            String state,
            String code,
            String providerError) {
        if (!sessionStore.consumeState(sessionId, provider, state)) {
            throw new InvalidOAuthRequestException("OAuth callback state is missing or invalid");
        }
        if (providerError != null && !providerError.isBlank()) {
            throw new OAuthAuthorizationException("OAuth provider authorization failed");
        }
        if (code == null || code.isBlank()) {
            throw new InvalidOAuthRequestException("OAuth callback code is missing");
        }
        OAuthTokenClient.TokenResponse token = tokenClient.exchange(provider, code, redirectUri(provider));
        sessionStore.connect(sessionId, provider, token);
        return frontendRedirect(provider);
    }

    public OAuthSessionStore.SessionStatus status(String sessionId) {
        return sessionStore.status(sessionId);
    }

    public void remove(String sessionId) {
        sessionStore.remove(sessionId);
    }

    private boolean isConfigured(OAuthProvider provider) {
        return provider == OAuthProvider.SPOTIFY
                ? hasText(spotifyProperties.getClientId()) && hasText(spotifyProperties.getClientSecret())
                : googleProperties.isConfigured();
    }

    private String clientId(OAuthProvider provider) {
        return provider == OAuthProvider.SPOTIFY ? spotifyProperties.getClientId() : googleProperties.getClientId();
    }

    private String redirectUri(OAuthProvider provider) {
        if (provider == OAuthProvider.SPOTIFY) {
            return spotifyRedirectUri;
        }
        return googleProperties.getRedirectUri();
    }

    private String scope(OAuthProvider provider) {
        if (provider == OAuthProvider.SPOTIFY) {
            return SPOTIFY_SCOPE;
        }
        return hasText(googleProperties.getScope())
                ? googleProperties.getScope()
                : "https://www.googleapis.com/auth/youtube";
    }

    private String authorizationEndpoint(OAuthProvider provider) {
        return provider == OAuthProvider.SPOTIFY ? SPOTIFY_AUTHORIZATION_ENDPOINT : GOOGLE_AUTHORIZATION_ENDPOINT;
    }

    private URI frontendRedirect(OAuthProvider provider) {
        return UriComponentsBuilder.fromUriString(frontendPublicUrl)
                .queryParam("connected", provider.routeName())
                .build()
                .toUri();
    }

    private String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AuthorizationStart(String sessionId, URI authorizationUri) {
    }
}
